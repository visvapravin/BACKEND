package com.forumx.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forumx.ForumXApplication;
import com.forumx.auth.dto.request.LoginRequest;
import com.forumx.auth.entity.Role;
import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserRole;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.invitation.dto.request.AcceptInvitationRequest;
import com.forumx.auth.invitation.dto.request.CreateInvitationRequest;
import com.forumx.auth.invitation.entity.InvitationStatus;
import com.forumx.auth.invitation.entity.ModeratorInvitation;
import com.forumx.auth.invitation.repository.ModeratorInvitationRepository;
import com.forumx.auth.repository.RoleRepository;
import com.forumx.auth.repository.UserRepository;
import com.forumx.auth.repository.UserRoleRepository;
import com.forumx.platform.auth.dto.PlatformLoginRequest;
import com.forumx.platform.invitation.dto.AcceptTenantAdminInvitationRequest;
import com.forumx.platform.invitation.entity.TenantAdminInvitation;
import com.forumx.platform.invitation.repository.TenantAdminInvitationRepository;
import com.forumx.platform.tenant.service.PlatformTenantService;
import com.forumx.security.jwt.JwtTokenProvider;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@SpringBootTest(classes = ForumXApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class MultiTenantUserMembershipIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private ModeratorInvitationRepository moderatorInvitationRepository;
    @Autowired private TenantAdminInvitationRepository tenantAdminInvitationRepository;
    @Autowired private PlatformTenantService platformTenantService;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JwtTokenProvider jwt;

    private static final String PASSWORD = "TestPassword123!";
    private Tenant tenantA;
    private Tenant tenantB;
    private Tenant tenantC;
    private Tenant tenantD;
    private User platformAdmin;
    private String platformToken;

    @BeforeEach
    void setUp() throws Exception {
        tenantA = createTenant("Tenant Alpha " + System.nanoTime(), "tenant-a-" + System.nanoTime());
        tenantB = createTenant("Tenant Beta " + System.nanoTime(), "tenant-b-" + System.nanoTime());
        tenantC = createTenant("Tenant Gamma " + System.nanoTime(), "tenant-c-" + System.nanoTime());
        tenantD = createTenant("Tenant Delta " + System.nanoTime(), "tenant-d-" + System.nanoTime());

        // Platform Admin setup
        platformAdmin = userRepository.findByUsernameAndDeletedFalse("platform_admin_test")
                .orElseGet(() -> {
                    User pa = User.builder()
                            .tenant(null)
                            .username("platform_admin_test")
                            .email("platform_admin_test@forumx.internal")
                            .passwordHash(encoder.encode(PASSWORD))
                            .enabled(true)
                            .emailVerified(true)
                            .status(User.UserStatus.ACTIVE)
                            .build();
                    pa = userRepository.save(pa);
                    Role paRole = roleRepository.findByRoleName(RoleType.PLATFORM_ADMIN).orElseThrow();
                    userRoleRepository.save(UserRole.builder().user(pa).tenant(null).role(paRole).active(true).build());
                    return pa;
                });

        PlatformLoginRequest loginReq = new PlatformLoginRequest();
        loginReq.setUsernameOrEmail(platformAdmin.getUsername());
        loginReq.setPassword(PASSWORD);
        String paLoginResp = mvc.perform(post("/api/v1/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        platformToken = mapper.readTree(paLoginResp).path("data").path("accessToken").asText();
    }

    private Tenant createTenant(String name, String slug) {
        return tenantRepository.save(Tenant.builder()
                .name(name)
                .slug(slug)
                .status(Tenant.TenantStatus.ACTIVE)
                .subscriptionPlan(Tenant.SubscriptionPlan.FREE)
                .maxUsers(100).storageQuotaMB(1024L)
                .timezone("UTC").locale("en_US")
                .build());
    }

    @Test
    @DisplayName("TEST 1: New email receives Tenant Admin invitation -> 1 User, 1 membership, TENANT_ADMIN")
    void test1_newEmail_receivesTenantAdminInvitation() throws Exception {
        String email = "new_admin_" + System.nanoTime() + "@example.com";
        String rawToken = "rawTokenTest1_" + System.nanoTime();
        String tokenHash = hashToken(rawToken);

        tenantAdminInvitationRepository.save(TenantAdminInvitation.builder()
                .tenant(tenantA)
                .invitedBy(platformAdmin)
                .email(email)
                .role(RoleType.TENANT_ADMIN)
                .tokenHash(tokenHash)
                .status(InvitationStatus.PENDING)
                .expiresAt(java.time.Instant.now().plusSeconds(86400))
                .build());

        AcceptTenantAdminInvitationRequest req = AcceptTenantAdminInvitationRequest.builder()
                .token(rawToken)
                .username("new_admin_user_" + System.nanoTime())
                .password(PASSWORD)
                .confirmPassword(PASSWORD)
                .build();

        mvc.perform(post("/api/v1/auth/tenant-admin-invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        User user = userRepository.findByEmailAndDeletedFalse(email).orElseThrow();
        List<UserRole> roles = userRoleRepository.findActiveRolesByUserIdAndTenantId(user.getId(), tenantA.getId());
        assertEquals(1, roles.size());
        assertEquals(RoleType.TENANT_ADMIN, roles.get(0).getRole().getRoleName());
    }

    @Test
    @DisplayName("TEST 2: Existing USER receives Tenant Admin invitation -> same User ID, no duplicate User, TENANT_ADMIN assigned")
    void test2_existingUser_receivesTenantAdminInvitation() throws Exception {
        String email = "shared_user_" + System.nanoTime() + "@example.com";
        User existingUser = userRepository.save(User.builder()
                .tenant(tenantA)
                .username("shared_user_" + System.nanoTime())
                .email(email)
                .passwordHash(encoder.encode(PASSWORD))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build());

        Role userRole = roleRepository.findByRoleName(RoleType.USER).orElseThrow();
        userRoleRepository.save(UserRole.builder().user(existingUser).tenant(tenantA).role(userRole).active(true).build());

        long userCountBefore = userRepository.count();

        String rawToken = "rawTokenTest2_" + System.nanoTime();
        String tokenHash = hashToken(rawToken);

        tenantAdminInvitationRepository.save(TenantAdminInvitation.builder()
                .tenant(tenantA)
                .invitedBy(platformAdmin)
                .email(email)
                .role(RoleType.TENANT_ADMIN)
                .tokenHash(tokenHash)
                .status(InvitationStatus.PENDING)
                .expiresAt(java.time.Instant.now().plusSeconds(86400))
                .build());

        AcceptTenantAdminInvitationRequest req = AcceptTenantAdminInvitationRequest.builder()
                .token(rawToken)
                .password(PASSWORD)
                .confirmPassword(PASSWORD)
                .build();

        mvc.perform(post("/api/v1/auth/tenant-admin-invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        assertEquals(userCountBefore, userRepository.count(), "User count must not increase for existing user");
        assertTrue(userRoleRepository.existsActiveRoleByUserIdAndTenantIdAndRoleName(existingUser.getId(), tenantA.getId(), RoleType.TENANT_ADMIN));
    }

    @Test
    @DisplayName("TEST 3: Existing Tenant Admin receives invitation for DIFFERENT tenant -> User 42 has Tenant A (TENANT_ADMIN) & Tenant B (TENANT_ADMIN)")
    void test3_multiTenantAdmin_sameUser() throws Exception {
        String email = "visva_" + System.nanoTime() + "@gmail.com";
        User user = userRepository.save(User.builder()
                .tenant(tenantA)
                .username("visva_" + System.nanoTime())
                .email(email)
                .passwordHash(encoder.encode(PASSWORD))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build());

        Role taRole = roleRepository.findByRoleName(RoleType.TENANT_ADMIN).orElseThrow();
        userRoleRepository.save(UserRole.builder().user(user).tenant(tenantA).role(taRole).active(true).build());

        long userCountBefore = userRepository.count();

        // Platform admin invites same email to Tenant B
        String rawToken = "rawTokenTest3_" + System.nanoTime();
        tenantAdminInvitationRepository.save(TenantAdminInvitation.builder()
                .tenant(tenantB)
                .invitedBy(platformAdmin)
                .email(email)
                .role(RoleType.TENANT_ADMIN)
                .tokenHash(hashToken(rawToken))
                .status(InvitationStatus.PENDING)
                .expiresAt(java.time.Instant.now().plusSeconds(86400))
                .build());

        AcceptTenantAdminInvitationRequest req = AcceptTenantAdminInvitationRequest.builder()
                .token(rawToken)
                .password(PASSWORD)
                .confirmPassword(PASSWORD)
                .build();

        mvc.perform(post("/api/v1/auth/tenant-admin-invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        assertEquals(userCountBefore, userRepository.count(), "User count must remain unchanged");
        assertTrue(userRoleRepository.existsActiveRoleByUserIdAndTenantIdAndRoleName(user.getId(), tenantA.getId(), RoleType.TENANT_ADMIN));
        assertTrue(userRoleRepository.existsActiveRoleByUserIdAndTenantIdAndRoleName(user.getId(), tenantB.getId(), RoleType.TENANT_ADMIN));
    }

    @Test
    @DisplayName("TEST 4: Existing Moderator receives Tenant Admin invitation in same tenant -> promoted to TENANT_ADMIN")
    void test4_moderator_promotedTo_tenantAdmin() throws Exception {
        String email = "mod_to_admin_" + System.nanoTime() + "@example.com";
        User user = userRepository.save(User.builder()
                .tenant(tenantA)
                .username("mod_user_" + System.nanoTime())
                .email(email)
                .passwordHash(encoder.encode(PASSWORD))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build());

        Role modRole = roleRepository.findByRoleName(RoleType.MODERATOR).orElseThrow();
        userRoleRepository.save(UserRole.builder().user(user).tenant(tenantA).role(modRole).active(true).build());

        String rawToken = "rawTokenTest4_" + System.nanoTime();
        tenantAdminInvitationRepository.save(TenantAdminInvitation.builder()
                .tenant(tenantA)
                .invitedBy(platformAdmin)
                .email(email)
                .role(RoleType.TENANT_ADMIN)
                .tokenHash(hashToken(rawToken))
                .status(InvitationStatus.PENDING)
                .expiresAt(java.time.Instant.now().plusSeconds(86400))
                .build());

        AcceptTenantAdminInvitationRequest req = AcceptTenantAdminInvitationRequest.builder()
                .token(rawToken)
                .password(PASSWORD)
                .confirmPassword(PASSWORD)
                .build();

        mvc.perform(post("/api/v1/auth/tenant-admin-invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        assertTrue(userRoleRepository.existsActiveRoleByUserIdAndTenantIdAndRoleName(user.getId(), tenantA.getId(), RoleType.TENANT_ADMIN));
    }

    @Test
    @DisplayName("TEST 5: Existing user receives Moderator invitation for another tenant -> Tenant A unchanged, Tenant B gets MODERATOR")
    void test5_moderatorInvitation_crossTenant() throws Exception {
        String email = "cross_mod_" + System.nanoTime() + "@example.com";
        User user = userRepository.save(User.builder()
                .tenant(tenantA)
                .username("cross_mod_user_" + System.nanoTime())
                .email(email)
                .passwordHash(encoder.encode(PASSWORD))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build());

        Role taRole = roleRepository.findByRoleName(RoleType.TENANT_ADMIN).orElseThrow();
        userRoleRepository.save(UserRole.builder().user(user).tenant(tenantA).role(taRole).active(true).build());

        String rawToken = "rawTokenTest5_" + System.nanoTime();
        moderatorInvitationRepository.save(ModeratorInvitation.builder()
                .tenant(tenantB)
                .invitedBy(platformAdmin)
                .email(email)
                .role(RoleType.MODERATOR)
                .tokenHash(hashToken(rawToken))
                .status(InvitationStatus.PENDING)
                .expiresAt(java.time.Instant.now().plusSeconds(86400))
                .build());

        AcceptInvitationRequest req = AcceptInvitationRequest.builder()
                .token(rawToken)
                .password(PASSWORD)
                .confirmPassword(PASSWORD)
                .build();

        mvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        assertTrue(userRoleRepository.existsActiveRoleByUserIdAndTenantIdAndRoleName(user.getId(), tenantA.getId(), RoleType.TENANT_ADMIN));
        assertTrue(userRoleRepository.existsActiveRoleByUserIdAndTenantIdAndRoleName(user.getId(), tenantB.getId(), RoleType.MODERATOR));
    }

    @Test
    @DisplayName("TEST 8, 9, 10, 11: Multi-tenant login & JWT tenant scoping (Tenant A=TENANT_ADMIN, Tenant B=TENANT_ADMIN, Tenant C=MODERATOR)")
    void test8_to_11_multiTenantLoginAndJwtScoping() throws Exception {
        String email = "triple_tenant_" + System.nanoTime() + "@example.com";
        User user = userRepository.save(User.builder()
                .tenant(tenantA)
                .username("triple_user_" + System.nanoTime())
                .email(email)
                .passwordHash(encoder.encode(PASSWORD))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build());

        Role taRole = roleRepository.findByRoleName(RoleType.TENANT_ADMIN).orElseThrow();
        Role modRole = roleRepository.findByRoleName(RoleType.MODERATOR).orElseThrow();

        userRoleRepository.save(UserRole.builder().user(user).tenant(tenantA).role(taRole).active(true).build());
        userRoleRepository.save(UserRole.builder().user(user).tenant(tenantB).role(taRole).active(true).build());
        userRoleRepository.save(UserRole.builder().user(user).tenant(tenantC).role(modRole).active(true).build());

        // TEST 9: Login into Tenant A
        LoginRequest loginReqA = LoginRequest.builder()
                .tenantSlug(tenantA.getSlug())
                .usernameOrEmail(email)
                .password(PASSWORD)
                .build();
        String loginA = mvc.perform(post("/api/v1/auth/login")
                        .header("X-Tenant", tenantA.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(loginReqA)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String tokenA = mapper.readTree(loginA).path("data").path("accessToken").asText();
        assertEquals(tenantA.getId(), jwt.extractTenantId(tokenA));
        assertEquals(List.of("TENANT_ADMIN"), mapper.readerForListOf(String.class).readValue(mapper.readTree(loginA).path("data").path("roles")));

        // TEST 10: Login into Tenant B
        LoginRequest loginReqB = LoginRequest.builder()
                .tenantSlug(tenantB.getSlug())
                .usernameOrEmail(email)
                .password(PASSWORD)
                .build();
        String loginB = mvc.perform(post("/api/v1/auth/login")
                        .header("X-Tenant", tenantB.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(loginReqB)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String tokenB = mapper.readTree(loginB).path("data").path("accessToken").asText();
        assertEquals(tenantB.getId(), jwt.extractTenantId(tokenB));
        assertEquals(List.of("TENANT_ADMIN"), mapper.readerForListOf(String.class).readValue(mapper.readTree(loginB).path("data").path("roles")));

        // TEST 11: Login into Tenant C
        LoginRequest loginReqC = LoginRequest.builder()
                .tenantSlug(tenantC.getSlug())
                .usernameOrEmail(email)
                .password(PASSWORD)
                .build();
        String loginC = mvc.perform(post("/api/v1/auth/login")
                        .header("X-Tenant", tenantC.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(loginReqC)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String tokenC = mapper.readTree(loginC).path("data").path("accessToken").asText();
        assertEquals(tenantC.getId(), jwt.extractTenantId(tokenC));
        assertEquals(List.of("MODERATOR"), mapper.readerForListOf(String.class).readValue(mapper.readTree(loginC).path("data").path("roles")));

        // TEST 8: Verify GET /api/v1/auth/memberships returns all 3 workspaces
        String membershipsResp = mvc.perform(get("/api/v1/auth/memberships")
                        .header("X-Tenant", tenantA.getSlug())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(3, mapper.readTree(membershipsResp).path("data").size());
    }

    @Test
    @DisplayName("TEST 12: User has no membership in Tenant D -> Attempt login -> 401 Unauthorized")
    void test12_unauthorizedTenantLogin_isRejected() throws Exception {
        String email = "isolated_user_" + System.nanoTime() + "@example.com";
        User user = userRepository.save(User.builder()
                .tenant(tenantA)
                .username("isolated_user_" + System.nanoTime())
                .email(email)
                .passwordHash(encoder.encode(PASSWORD))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build());

        Role taRole = roleRepository.findByRoleName(RoleType.TENANT_ADMIN).orElseThrow();
        userRoleRepository.save(UserRole.builder().user(user).tenant(tenantA).role(taRole).active(true).build());

        LoginRequest loginReq = LoginRequest.builder()
                .tenantSlug(tenantD.getSlug())
                .usernameOrEmail(email)
                .password(PASSWORD)
                .build();

        mvc.perform(post("/api/v1/auth/login")
                        .header("X-Tenant", tenantD.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TEST 14: Tenant A deactivated -> Tenant A login fails (401), Tenant B login still works (200), User not globally disabled")
    void test14_tenantDeactivation_preservesOtherTenants() throws Exception {
        String email = "deact_test_" + System.nanoTime() + "@example.com";
        User user = userRepository.save(User.builder()
                .tenant(tenantA)
                .username("deact_user_" + System.nanoTime())
                .email(email)
                .passwordHash(encoder.encode(PASSWORD))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build());

        Role taRole = roleRepository.findByRoleName(RoleType.TENANT_ADMIN).orElseThrow();
        Role modRole = roleRepository.findByRoleName(RoleType.MODERATOR).orElseThrow();

        userRoleRepository.save(UserRole.builder().user(user).tenant(tenantA).role(taRole).active(true).build());
        userRoleRepository.save(UserRole.builder().user(user).tenant(tenantB).role(modRole).active(true).build());

        // Deactivate Tenant A
        platformTenantService.deactivateTenant(tenantA.getId());

        LoginRequest loginReqA = LoginRequest.builder()
                .tenantSlug(tenantA.getSlug())
                .usernameOrEmail(email)
                .password(PASSWORD)
                .build();

        // Tenant A login fails
        mvc.perform(post("/api/v1/auth/login")
                        .header("X-Tenant", tenantA.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(loginReqA)))
                .andExpect(status().isUnauthorized());

        LoginRequest loginReqB = LoginRequest.builder()
                .tenantSlug(tenantB.getSlug())
                .usernameOrEmail(email)
                .password(PASSWORD)
                .build();

        // Tenant B login succeeds
        mvc.perform(post("/api/v1/auth/login")
                        .header("X-Tenant", tenantB.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(loginReqB)))
                .andExpect(status().isOk());

        // User account remains globally active
        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertTrue(reloaded.isEnabled());
        assertEquals(User.UserStatus.ACTIVE, reloaded.getStatus());
    }

    @Test
    @DisplayName("TEST 16: Accept invitation twice -> safe idempotent response")
    void test16_duplicateInvitationAcceptance_isSafe() throws Exception {
        String email = "idempotent_" + System.nanoTime() + "@example.com";
        String rawToken = "rawTokenTest16_" + System.nanoTime();

        tenantAdminInvitationRepository.save(TenantAdminInvitation.builder()
                .tenant(tenantA)
                .invitedBy(platformAdmin)
                .email(email)
                .role(RoleType.TENANT_ADMIN)
                .tokenHash(hashToken(rawToken))
                .status(InvitationStatus.PENDING)
                .expiresAt(java.time.Instant.now().plusSeconds(86400))
                .build());

        AcceptTenantAdminInvitationRequest req = AcceptTenantAdminInvitationRequest.builder()
                .token(rawToken)
                .username("idemp_user_" + System.nanoTime())
                .password(PASSWORD)
                .confirmPassword(PASSWORD)
                .build();

        mvc.perform(post("/api/v1/auth/tenant-admin-invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Second acceptance attempt -> 409 Conflict (or 400 Bad Request)
        mvc.perform(post("/api/v1/auth/tenant-admin-invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("TEST 17: Tenant Admin attempts to access Platform Admin endpoint -> 403 Forbidden")
    void test17_tenantAdmin_cannotAccessPlatformEndpoints() throws Exception {
        String email = "ta_iso_" + System.nanoTime() + "@example.com";
        User user = userRepository.save(User.builder()
                .tenant(tenantA)
                .username("ta_iso_" + System.nanoTime())
                .email(email)
                .passwordHash(encoder.encode(PASSWORD))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build());

        Role taRole = roleRepository.findByRoleName(RoleType.TENANT_ADMIN).orElseThrow();
        userRoleRepository.save(UserRole.builder().user(user).tenant(tenantA).role(taRole).active(true).build());

        LoginRequest loginReq = LoginRequest.builder()
                .tenantSlug(tenantA.getSlug())
                .usernameOrEmail(email)
                .password(PASSWORD)
                .build();

        String loginResp = mvc.perform(post("/api/v1/auth/login")
                        .header("X-Tenant", tenantA.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(loginResp).path("data").path("accessToken").asText();

        mvc.perform(get("/api/v1/platform/tenants")
                        .header("X-Tenant", tenantA.getSlug())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TEST 18: Platform Admin attempts tenant-only endpoint without tenant context -> 403 Forbidden")
    void test18_platformAdmin_cannotAccessTenantOnlyEndpoints() throws Exception {
        mvc.perform(get("/api/v1/admin/staff/moderators")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isForbidden());
    }

    private String hashToken(String rawToken) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
