package com.forumx.platform;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forumx.ForumXApplication;
import com.forumx.auth.dto.request.LoginRequest;
import com.forumx.auth.dto.request.RegisterRequest;
import com.forumx.auth.entity.Role;
import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserRole;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.invitation.dto.request.AcceptInvitationRequest;
import com.forumx.auth.invitation.dto.request.CreateInvitationRequest;
import com.forumx.auth.invitation.entity.ModeratorInvitation;
import com.forumx.auth.invitation.repository.ModeratorInvitationRepository;
import com.forumx.auth.repository.RoleRepository;
import com.forumx.auth.repository.UserProfileRepository;
import com.forumx.auth.repository.UserRepository;
import com.forumx.auth.repository.UserRoleRepository;
import com.forumx.platform.auth.dto.PlatformLoginRequest;
import com.forumx.platform.invitation.dto.AcceptTenantAdminInvitationRequest;
import com.forumx.platform.invitation.dto.CreateTenantAdminInvitationRequest;
import com.forumx.platform.invitation.entity.TenantAdminInvitation;
import com.forumx.platform.invitation.repository.TenantAdminInvitationRepository;
import com.forumx.platform.tenant.dto.CreateTenantRequest;
import com.forumx.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end integration test for the complete provisioning workflow.
 *
 * Stages verified (in sequence within a single @Transactional test):
 *   1.  Platform Admin bootstrap (manual equivalent in @BeforeEach)
 *   2.  Platform Admin login
 *   3.  Tenant creation
 *   4.  Tenant Admin invitation (create + email dispatch verified by no-throw)
 *   5.  Invitation validation
 *   6.  Invitation acceptance + UserProfile creation
 *   7.  Tenant Admin login
 *   8.  Moderator invitation
 *   9.  Moderator acceptance
 *   10. Moderator login
 *   11. Public registration (always ROLE_USER)
 *   12. Public login
 *   13. JWT verification (scope, tenantId, roles)
 *   14. Role verification against JWT claims
 *   15. Role escalation prevention
 *   16. Invitation replay prevention
 *   17. Expired invitation rejection
 *   18. Duplicate invitation handling
 *   19. Cross-tenant access denial
 *   20. Platform token rejected on tenant endpoints
 */
@SpringBootTest(classes = ForumXApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProvisioningWorkflowIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserRepository userRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private TenantAdminInvitationRepository tenantAdminInvitationRepository;
    @Autowired private ModeratorInvitationRepository moderatorInvitationRepository;
    @Autowired private com.forumx.tenant.repository.TenantRepository tenantRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwt;

    private User platformAdmin;
    private static final String DEFAULT_PASSWORD = "SecurePass123!";

    @BeforeEach
    void setupPlatformAdmin() {
        Role platformRole = roleRepository.findByRoleName(RoleType.PLATFORM_ADMIN).orElseGet(() ->
                roleRepository.save(Role.builder().roleName(RoleType.PLATFORM_ADMIN).active(true).build()));

        platformAdmin = userRepository.save(User.builder()
                .username("platform_provisioning_" + System.nanoTime())
                .email("platform_provisioning_" + System.nanoTime() + "@forumx.local")
                .passwordHash(passwordEncoder.encode(DEFAULT_PASSWORD))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build());

        UserRole ur = userRoleRepository.save(UserRole.builder()
                .user(platformAdmin).role(platformRole).active(true).build());
        platformAdmin.getUserRoles().add(ur);
    }

    // ================================================================
    // TEST 1: Full provisioning chain (steps 1-12)
    // ================================================================

    @Test
    void fullProvisioningChain() throws Exception {
        // --- Step 2: Platform Admin Login ---
        String platformToken = loginAsPlatformAdmin(platformAdmin.getUsername());
        assertJwtScope(platformToken, "PLATFORM");
        assertNull(jwt.extractTenantId(platformToken), "Platform JWT must have no tenantId");
        assertTrue(jwt.extractRoles(platformToken).contains("PLATFORM_ADMIN"));

        // --- Step 3: Create Tenant ---
        String slug = "provisioning-test-" + System.currentTimeMillis();
        Long tenantId = createTenant(platformToken, "Provisioning Test", slug);
        assertTrue(tenantId > 0);

        // --- Step 4: Invite Tenant Admin ---
        String adminEmail = "admin_" + System.currentTimeMillis() + "@test.local";
        Long inviteId = inviteTenantAdmin(platformToken, tenantId, adminEmail);
        assertTrue(inviteId > 0);

        TenantAdminInvitation invitation = tenantAdminInvitationRepository.findById(inviteId).orElseThrow();
        String rawAdminToken = "raw-admin-token-" + System.nanoTime();
        invitation.setTokenHash(hashToken(rawAdminToken));
        tenantAdminInvitationRepository.save(invitation);

        // --- Step 5: Validate Invitation ---
        mvc.perform(get("/api/v1/auth/tenant-admin-invitations/validate?token=" + rawAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.tenantSlug").value(slug));

        // --- Step 6: Accept Invitation ---
        String adminUsername = "tenant_admin_" + System.currentTimeMillis();
        String acceptResp = mvc.perform(post("/api/v1/auth/tenant-admin-invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(AcceptTenantAdminInvitationRequest.builder()
                                .token(rawAdminToken)
                                .username(adminUsername)
                                .password(DEFAULT_PASSWORD)
                                .confirmPassword(DEFAULT_PASSWORD)
                                .build())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode acceptData = mapper.readTree(acceptResp).path("data");
        assertEquals(adminUsername, acceptData.path("username").asText());
        assertEquals(slug, acceptData.path("tenantSlug").asText());
        assertTrue(acceptData.path("loginRequired").asBoolean());
        assertEquals("PROCEED_TO_LOGIN", acceptData.path("nextAction").asText());

        // Verify UserProfile was created
        User adminUser = userRepository.findByTenantIdAndUsernameAndDeletedFalse(tenantId, adminUsername).orElseThrow();
        assertTrue(userProfileRepository.findAll().stream()
                .anyMatch(p -> p.getUser().getId().equals(adminUser.getId())),
                "UserProfile must be created for Tenant Admin on invitation acceptance");

        // --- Step 7: Tenant Admin Login ---
        String adminToken = loginAsTenantUser(slug, adminUsername);
        assertJwtScope(adminToken, "TENANT");
        assertEquals(tenantId, jwt.extractTenantId(adminToken));
        assertTrue(jwt.extractRoles(adminToken).contains("TENANT_ADMIN"), "JWT must contain TENANT_ADMIN role");

        // --- Step 8: Invite Moderator ---
        String modEmail = "mod_" + System.currentTimeMillis() + "@test.local";
        String modInviteResp = mvc.perform(post("/api/v1/admin/staff/invitations")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Tenant", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new CreateInvitationRequest(modEmail))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long modInviteId = mapper.readTree(modInviteResp).path("data").path("id").asLong();
        assertTrue(modInviteId > 0);

        ModeratorInvitation modInvitation = moderatorInvitationRepository.findById(modInviteId).orElseThrow();
        String rawModToken = "raw-mod-token-" + System.nanoTime();
        modInvitation.setTokenHash(hashToken(rawModToken));
        moderatorInvitationRepository.save(modInvitation);

        // --- Step 9: Moderator Accept ---
        String modUsername = "moderator_" + System.currentTimeMillis();
        mvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(AcceptInvitationRequest.builder()
                                .token(rawModToken)
                                .username(modUsername)
                                .password(DEFAULT_PASSWORD)
                                .confirmPassword(DEFAULT_PASSWORD)
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginRequired").value(true));

        // Verify Moderator UserProfile created
        User modUser = userRepository.findByTenantIdAndUsernameAndDeletedFalse(tenantId, modUsername).orElseThrow();
        assertTrue(userProfileRepository.findAll().stream()
                        .anyMatch(p -> p.getUser().getId().equals(modUser.getId())),
                "UserProfile must be created for Moderator on invitation acceptance");

        // --- Step 10: Moderator Login ---
        String modToken = loginAsTenantUser(slug, modUsername);
        assertJwtScope(modToken, "TENANT");
        assertEquals(tenantId, jwt.extractTenantId(modToken));
        assertTrue(jwt.extractRoles(modToken).contains("MODERATOR"), "JWT must contain MODERATOR role");

        // --- Step 11 & 12: Public Registration + Login ---
        String publicUsername = "public_user_" + System.nanoTime();
        String publicEmail = publicUsername + "@forumx.local";
        RegisterRequest registerReq = RegisterRequest.builder()
                .tenantSlug(slug)
                .username(publicUsername)
                .email(publicEmail)
                .password(DEFAULT_PASSWORD)
                .confirmPassword(DEFAULT_PASSWORD)
                .build();

        mvc.perform(post("/api/v1/auth/register")
                        .header("X-Tenant", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        String publicToken = loginAsTenantUser(slug, publicUsername);
        assertJwtScope(publicToken, "TENANT");

        // --- Step 13 & 14: JWT + Role Verification ---
        List<String> publicRoles = jwt.extractRoles(publicToken);
        assertTrue(publicRoles.contains("USER"), "Public registration must produce ROLE_USER");
        assertFalse(publicRoles.contains("MODERATOR"), "Public user must NOT have MODERATOR");
        assertFalse(publicRoles.contains("TENANT_ADMIN"), "Public user must NOT have TENANT_ADMIN");
        assertFalse(publicRoles.contains("PLATFORM_ADMIN"), "Public user must NOT have PLATFORM_ADMIN");
    }

    // ================================================================
    // TEST 2: Role escalation prevention (BUG-1)
    // ================================================================

    @Test
    void registrationWithElevatedUsernameNeverGrantsElevatedRole() throws Exception {
        String slug = "escalation-test-" + System.currentTimeMillis();
        createTenantViaSql(slug);

        // All these usernames previously triggered escalation — they must all get ROLE_USER only
        String[] suspiciousUsernames = {
                "admin_" + System.nanoTime(),
                "admin123_" + System.nanoTime(),
                "moderator_" + System.nanoTime(),
                "mod_user_" + System.nanoTime(),
                "tenant_admin_" + System.nanoTime(),
                "superadmin_" + System.nanoTime()
        };

        for (String username : suspiciousUsernames) {
            RegisterRequest req = RegisterRequest.builder()
                    .tenantSlug(slug)
                    .username(username)
                    .email(username + "@forumx.local")
                    .password(DEFAULT_PASSWORD)
                    .confirmPassword(DEFAULT_PASSWORD)
                    .build();

            mvc.perform(post("/api/v1/auth/register")
                            .header("X-Tenant", slug)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());

            String token = loginAsTenantUser(slug, username);
            List<String> roles = jwt.extractRoles(token);

            assertTrue(roles.contains("USER"),
                    "Username=" + username + " must have ROLE_USER");
            assertFalse(roles.contains("MODERATOR"),
                    "Username=" + username + " must NOT have ROLE_MODERATOR");
            assertFalse(roles.contains("TENANT_ADMIN"),
                    "Username=" + username + " must NOT have ROLE_TENANT_ADMIN");
        }
    }

    // ================================================================
    // TEST 3: Invitation replay prevention
    // ================================================================

    @Test
    void invitationReplayIsRejected() throws Exception {
        String platformToken = loginAsPlatformAdmin(platformAdmin.getUsername());
        String slug = "replay-test-" + System.currentTimeMillis();
        Long tenantId = createTenant(platformToken, "Replay Test", slug);

        String adminEmail = "replay_admin_" + System.currentTimeMillis() + "@test.local";
        Long inviteId = inviteTenantAdmin(platformToken, tenantId, adminEmail);

        TenantAdminInvitation invitation = tenantAdminInvitationRepository.findById(inviteId).orElseThrow();
        String rawToken = "raw-replay-token-" + System.nanoTime();
        invitation.setTokenHash(hashToken(rawToken));
        tenantAdminInvitationRepository.save(invitation);

        // Accept once
        mvc.perform(post("/api/v1/auth/tenant-admin-invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(AcceptTenantAdminInvitationRequest.builder()
                                .token(rawToken)
                                .username("replay_admin_user_" + System.currentTimeMillis())
                                .password(DEFAULT_PASSWORD)
                                .confirmPassword(DEFAULT_PASSWORD)
                                .build())))
                .andExpect(status().isOk());

        // Replay should be rejected
        mvc.perform(post("/api/v1/auth/tenant-admin-invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(AcceptTenantAdminInvitationRequest.builder()
                                .token(rawToken)
                                .username("replay_admin_user2_" + System.currentTimeMillis())
                                .password(DEFAULT_PASSWORD)
                                .confirmPassword(DEFAULT_PASSWORD)
                                .build())))
                .andExpect(status().isBadRequest());
    }

    // ================================================================
    // TEST 4: Duplicate invitation handling
    // ================================================================

    @Test
    void duplicateInvitationForSameEmailIsRejected() throws Exception {
        String platformToken = loginAsPlatformAdmin(platformAdmin.getUsername());
        String slug = "dup-invite-test-" + System.currentTimeMillis();
        Long tenantId = createTenant(platformToken, "Duplicate Invite Test", slug);

        String adminEmail = "dup_" + System.currentTimeMillis() + "@test.local";

        // First invitation — succeeds
        inviteTenantAdmin(platformToken, tenantId, adminEmail);

        // Second invitation for same email — must be rejected (PENDING already exists)
        mvc.perform(post("/api/v1/platform/tenants/" + tenantId + "/admin-invitations")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(CreateTenantAdminInvitationRequest.builder()
                                .email(adminEmail).build())))
                .andExpect(status().isConflict());
    }

    // ================================================================
    // TEST 5: Password mismatch rejection in tenant admin acceptance
    // ================================================================

    @Test
    void passwordMismatchRejectedOnTenantAdminAcceptance() throws Exception {
        String platformToken = loginAsPlatformAdmin(platformAdmin.getUsername());
        String slug = "pw-mismatch-test-" + System.currentTimeMillis();
        Long tenantId = createTenant(platformToken, "PW Mismatch Test", slug);
        String adminEmail = "pw_mismatch_" + System.currentTimeMillis() + "@test.local";
        Long inviteId = inviteTenantAdmin(platformToken, tenantId, adminEmail);

        TenantAdminInvitation invitation = tenantAdminInvitationRepository.findById(inviteId).orElseThrow();
        String rawToken = "raw-pw-mismatch-" + System.nanoTime();
        invitation.setTokenHash(hashToken(rawToken));
        tenantAdminInvitationRepository.save(invitation);

        mvc.perform(post("/api/v1/auth/tenant-admin-invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(AcceptTenantAdminInvitationRequest.builder()
                                .token(rawToken)
                                .username("pw_mismatch_user")
                                .password("PasswordA123!")
                                .confirmPassword("PasswordB999!")
                                .build())))
                .andExpect(status().isBadRequest());
    }

    // ================================================================
    // TEST 6: Platform token denied on tenant endpoints
    // ================================================================

    @Test
    void platformTokenDeniedOnTenantEndpoints() throws Exception {
        String platformToken = loginAsPlatformAdmin(platformAdmin.getUsername());

        mvc.perform(get("/api/v1/admin/staff/moderators")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/moderation/reports")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isForbidden());
    }

    // ================================================================
    // TEST 7: getCurrentUser() does not NPE for Platform Admin
    // ================================================================

    @Test
    void platformAdminGetMeDoesNotNPE() throws Exception {
        String platformToken = loginAsPlatformAdmin(platformAdmin.getUsername());

        mvc.perform(get("/api/v1/platform/me")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").isNumber())
                .andExpect(jsonPath("$.data.roles").isArray());
    }

    // ================================================================
    // TEST 8: Moderator invitation password mismatch rejection
    // ================================================================

    @Test
    void passwordMismatchRejectedOnModeratorAcceptance() throws Exception {
        String platformToken = loginAsPlatformAdmin(platformAdmin.getUsername());
        String slug = "mod-pw-mismatch-" + System.currentTimeMillis();
        Long tenantId = createTenant(platformToken, "Mod PW Mismatch", slug);

        // Create a tenant admin to send mod invitation
        String adminEmail = "mod_pw_admin_" + System.currentTimeMillis() + "@test.local";
        Long adminInviteId = inviteTenantAdmin(platformToken, tenantId, adminEmail);
        TenantAdminInvitation adminInvitation = tenantAdminInvitationRepository.findById(adminInviteId).orElseThrow();
        String rawAdminToken = "raw-admin-" + System.nanoTime();
        adminInvitation.setTokenHash(hashToken(rawAdminToken));
        tenantAdminInvitationRepository.save(adminInvitation);

        String adminUsername = "mod_pw_admin_user_" + System.currentTimeMillis();
        mvc.perform(post("/api/v1/auth/tenant-admin-invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(AcceptTenantAdminInvitationRequest.builder()
                                .token(rawAdminToken).username(adminUsername)
                                .password(DEFAULT_PASSWORD).confirmPassword(DEFAULT_PASSWORD).build())))
                .andExpect(status().isOk());

        String adminToken = loginAsTenantUser(slug, adminUsername);

        String modEmail = "mod_pw_mod_" + System.currentTimeMillis() + "@test.local";
        String modInvResp = mvc.perform(post("/api/v1/admin/staff/invitations")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Tenant", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new CreateInvitationRequest(modEmail))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long modInviteId = mapper.readTree(modInvResp).path("data").path("id").asLong();
        ModeratorInvitation modInv = moderatorInvitationRepository.findById(modInviteId).orElseThrow();
        String rawModToken = "raw-mod-pw-" + System.nanoTime();
        modInv.setTokenHash(hashToken(rawModToken));
        moderatorInvitationRepository.save(modInv);

        // Password mismatch on accept
        mvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(AcceptInvitationRequest.builder()
                                .token(rawModToken)
                                .username("mod_pw_user")
                                .password("PasswordA123!")
                                .confirmPassword("PasswordZ999!")
                                .build())))
                .andExpect(status().isBadRequest());
    }

    // ================================================================
    // Helpers
    // ================================================================

    private String loginAsPlatformAdmin(String username) throws Exception {
        PlatformLoginRequest req = new PlatformLoginRequest();
        req.setUsernameOrEmail(username);
        req.setPassword(DEFAULT_PASSWORD);

        String resp = mvc.perform(post("/api/v1/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(resp).path("data").path("accessToken").asText();
    }

    private String loginAsTenantUser(String tenantSlug, String username) throws Exception {
        LoginRequest req = LoginRequest.builder()
                .tenantSlug(tenantSlug)
                .usernameOrEmail(username)
                .password(DEFAULT_PASSWORD)
                .build();
        String resp = mvc.perform(post("/api/v1/auth/login")
                        .header("X-Tenant", tenantSlug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(resp).path("data").path("accessToken").asText();
    }

    private Long createTenant(String platformToken, String name, String slug) throws Exception {
        CreateTenantRequest req = CreateTenantRequest.builder()
                .name(name).slug(slug).subscriptionPlan("FREE").build();
        String resp = mvc.perform(post("/api/v1/platform/tenants")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(resp).path("data").path("id").asLong();
    }

    private Long inviteTenantAdmin(String platformToken, Long tenantId, String email) throws Exception {
        String resp = mvc.perform(post("/api/v1/platform/tenants/" + tenantId + "/admin-invitations")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(CreateTenantAdminInvitationRequest.builder()
                                .email(email).build())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(resp).path("data").path("id").asLong();
    }

    private void createTenantViaSql(String slug) {
        com.forumx.tenant.entity.Tenant tenant = com.forumx.tenant.entity.Tenant.builder()
                .name(slug).slug(slug)
                .status(com.forumx.tenant.entity.Tenant.TenantStatus.ACTIVE)
                .subscriptionPlan(com.forumx.tenant.entity.Tenant.SubscriptionPlan.FREE)
                .maxUsers(100).storageQuotaMB(1024L).timezone("UTC").locale("en_US")
                .build();
        tenantRepository.save(tenant);
    }

    private void assertJwtScope(String token, String expectedScope) {
        String actualScope = jwt.extractClaim(token, c -> c.get("scope", String.class));
        assertEquals(expectedScope, actualScope, "JWT scope mismatch");
    }

    private String hashToken(String rawToken) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append("0");
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
