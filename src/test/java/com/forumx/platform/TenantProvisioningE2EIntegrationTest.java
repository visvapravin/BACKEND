package com.forumx.platform;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forumx.ForumXApplication;
import com.forumx.auth.dto.request.LoginRequest;
import com.forumx.auth.entity.Role;
import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserRole;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.invitation.dto.request.AcceptInvitationRequest;
import com.forumx.auth.invitation.dto.request.CreateInvitationRequest;
import com.forumx.auth.invitation.entity.ModeratorInvitation;
import com.forumx.auth.invitation.repository.ModeratorInvitationRepository;
import com.forumx.auth.repository.RoleRepository;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = ForumXApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class TenantProvisioningE2EIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserRepository users;
    @Autowired private UserRoleRepository userRoles;
    @Autowired private RoleRepository roles;
    @Autowired private TenantAdminInvitationRepository tenantAdminInvitationRepository;
    @Autowired private ModeratorInvitationRepository moderatorInvitationRepository;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JwtTokenProvider jwt;

    private User platformAdmin;
    private String defaultPassword = "SecurePass123!";

    @BeforeEach
    void setup() {
        Role platformRole = roles.findByRoleName(RoleType.PLATFORM_ADMIN).orElseGet(() ->
                roles.save(Role.builder().roleName(RoleType.PLATFORM_ADMIN).active(true).build()));

        platformAdmin = users.save(User.builder()
                .username("platform_e2e_" + System.nanoTime())
                .email("platform_e2e_" + System.nanoTime() + "@forumx.local")
                .passwordHash(encoder.encode(defaultPassword))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build());

        UserRole userRole = userRoles.save(UserRole.builder().user(platformAdmin).role(platformRole).active(true).build());
        platformAdmin.getUserRoles().add(userRole);
    }

    @Test
    void fullProvisioningChainAndTenantIsolationE2E() throws Exception {
        // 1. PLATFORM_ADMIN Login
        PlatformLoginRequest platformLoginReq = new PlatformLoginRequest();
        platformLoginReq.setUsernameOrEmail(platformAdmin.getUsername());
        platformLoginReq.setPassword(defaultPassword);

        String platformLoginResp = mvc.perform(post("/api/v1/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(platformLoginReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String platformToken = mapper.readTree(platformLoginResp).path("data").path("accessToken").asText();
        assertEquals("PLATFORM", jwt.extractClaim(platformToken, c -> c.get("scope", String.class)));

        // 2. PLATFORM_ADMIN Creates Tenant A
        String slugA = "tenant-a-" + System.currentTimeMillis();
        CreateTenantRequest createTenantA = CreateTenantRequest.builder()
                .name("Tenant Alpha")
                .slug(slugA)
                .subscriptionPlan("FREE")
                .build();

        String tenantAResp = mvc.perform(post("/api/v1/platform/tenants")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(createTenantA)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long tenantAId = mapper.readTree(tenantAResp).path("data").path("id").asLong();
        assertTrue(tenantAId > 0);

        // Duplicate slug rejection
        mvc.perform(post("/api/v1/platform/tenants")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(createTenantA)))
                .andExpect(status().isBadRequest());

        // List tenants
        mvc.perform(get("/api/v1/platform/tenants")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk());

        // 3. PLATFORM_ADMIN Invites Tenant Admin A
        String adminEmailA = "admin_a_" + System.currentTimeMillis() + "@tenant-a.com";
        CreateTenantAdminInvitationRequest inviteAdminA = CreateTenantAdminInvitationRequest.builder()
                .email(adminEmailA)
                .build();

        String adminInviteAResp = mvc.perform(post("/api/v1/platform/tenants/" + tenantAId + "/admin-invitations")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(inviteAdminA)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long invitationAId = mapper.readTree(adminInviteAResp).path("data").path("id").asLong();
        TenantAdminInvitation invitationA = tenantAdminInvitationRepository.findById(invitationAId).orElseThrow();

        // Validate invitation
        // We can inspect hashed token in db or simulate raw token acceptance directly
        // For test, we look up invitation in repository and set raw token hash
        String rawTokenA = "raw-token-admin-a-" + System.nanoTime();
        invitationA.setTokenHash(hashToken(rawTokenA));
        tenantAdminInvitationRepository.save(invitationA);

        mvc.perform(get("/api/v1/auth/tenant-admin-invitations/validate?token=" + rawTokenA))
                .andExpect(status().isOk());

        // 4. Accept Tenant Admin Invitation
        String adminUsernameA = "tenant_admin_a_" + System.currentTimeMillis();
        AcceptTenantAdminInvitationRequest acceptAdminA = AcceptTenantAdminInvitationRequest.builder()
                .token(rawTokenA)
                .username(adminUsernameA)
                .password(defaultPassword)
                .confirmPassword(defaultPassword)
                .build();

        mvc.perform(post("/api/v1/auth/tenant-admin-invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(acceptAdminA)))
                .andExpect(status().isOk());

        // 5. Tenant Admin A Login
        LoginRequest adminLoginA = LoginRequest.builder()
                .tenantSlug(slugA)
                .usernameOrEmail(adminUsernameA)
                .password(defaultPassword)
                .build();

        String adminLoginAResp = mvc.perform(post("/api/v1/auth/login")
                        .header("X-Tenant", slugA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(adminLoginA)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String adminTokenA = mapper.readTree(adminLoginAResp).path("data").path("accessToken").asText();
        assertEquals("TENANT", jwt.extractClaim(adminTokenA, c -> c.get("scope", String.class)));
        assertEquals(tenantAId, jwt.extractTenantId(adminTokenA));
        assertTrue(jwt.extractRoles(adminTokenA).contains("TENANT_ADMIN"));

        // 6. Tenant Admin A Invites Moderator A (Phase C / B flow)
        String modEmailA = "mod_a_" + System.currentTimeMillis() + "@tenant-a.com";
        CreateInvitationRequest inviteModReqA = new CreateInvitationRequest();
        inviteModReqA.setEmail(modEmailA);

        String modInviteAResp = mvc.perform(post("/api/v1/admin/staff/invitations")
                        .header("Authorization", "Bearer " + adminTokenA)
                        .header("X-Tenant", slugA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(inviteModReqA)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long modInviteIdA = mapper.readTree(modInviteAResp).path("data").path("id").asLong();
        ModeratorInvitation modInvitationA = moderatorInvitationRepository.findById(modInviteIdA).orElseThrow();

        String rawTokenModA = "raw-token-mod-a-" + System.nanoTime();
        modInvitationA.setTokenHash(hashToken(rawTokenModA));
        moderatorInvitationRepository.save(modInvitationA);

        // Moderator A accepts invitation
        String modUsernameA = "mod_user_a_" + System.currentTimeMillis();
        AcceptInvitationRequest acceptModA = new AcceptInvitationRequest();
        acceptModA.setToken(rawTokenModA);
        acceptModA.setUsername(modUsernameA);
        acceptModA.setPassword(defaultPassword);
        acceptModA.setConfirmPassword(defaultPassword);

        mvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(acceptModA)))
                .andExpect(status().isOk());

        // Moderator A Login
        LoginRequest modLoginA = LoginRequest.builder()
                .tenantSlug(slugA)
                .usernameOrEmail(modUsernameA)
                .password(defaultPassword)
                .build();

        String modLoginAResp = mvc.perform(post("/api/v1/auth/login")
                        .header("X-Tenant", slugA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(modLoginA)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String modTokenA = mapper.readTree(modLoginAResp).path("data").path("accessToken").asText();
        assertEquals("TENANT", jwt.extractClaim(modTokenA, c -> c.get("scope", String.class)));
        assertTrue(jwt.extractRoles(modTokenA).contains("MODERATOR"));

        // Moderator A access moderation queue
        mvc.perform(get("/api/v1/moderation/reports")
                        .header("Authorization", "Bearer " + modTokenA)
                        .header("X-Tenant", slugA))
                .andExpect(status().isOk());

        // 7. Repeat for Tenant B to verify Multi-Tenant Isolation
        String slugB = "tenant-b-" + System.currentTimeMillis();
        CreateTenantRequest createTenantB = CreateTenantRequest.builder()
                .name("Tenant Beta")
                .slug(slugB)
                .build();

        String tenantBResp = mvc.perform(post("/api/v1/platform/tenants")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(createTenantB)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long tenantBId = mapper.readTree(tenantBResp).path("data").path("id").asLong();
        assertTrue(tenantBId > tenantAId);

        // 8. Cross-Tenant Denial Checks
        // Tenant Admin A tries to access Tenant B staff endpoint
        mvc.perform(get("/api/v1/admin/staff/moderators")
                        .header("Authorization", "Bearer " + adminTokenA)
                        .header("X-Tenant", slugB))
                .andExpect(status().isUnauthorized());

        // Moderator A tries to access Tenant B moderation queue
        mvc.perform(get("/api/v1/moderation/reports")
                        .header("Authorization", "Bearer " + modTokenA)
                        .header("X-Tenant", slugB))
                .andExpect(status().isUnauthorized());
    }

    private String hashToken(String rawToken) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
