package com.forumx.platform;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forumx.ForumXApplication;
import com.forumx.auth.dto.request.LoginRequest;
import com.forumx.auth.entity.Role;
import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserRole;
import com.forumx.auth.enums.RoleType;
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

/**
 * Integration test for the full Tenant Admin Invitation -> Validation -> Acceptance -> Login -> Scope Isolation lifecycle.
 */
@SpringBootTest(classes = ForumXApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
public class TenantAdminInvitationAcceptanceIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserRepository userRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private TenantAdminInvitationRepository tenantAdminInvitationRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private User platformAdmin;
    private String platformToken;
    private final String defaultPassword = "SecurePassword123!";

    @BeforeEach
    void setUp() throws Exception {
        Role platformRole = roleRepository.findByRoleName(RoleType.PLATFORM_ADMIN).orElseGet(() ->
                roleRepository.save(Role.builder().roleName(RoleType.PLATFORM_ADMIN).active(true).build()));

        platformAdmin = userRepository.save(User.builder()
                .username("plat_admin_" + System.nanoTime())
                .email("plat_admin_" + System.nanoTime() + "@forumx.local")
                .passwordHash(passwordEncoder.encode(defaultPassword))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build());

        UserRole userRole = userRoleRepository.save(UserRole.builder().user(platformAdmin).role(platformRole).active(true).build());
        platformAdmin.getUserRoles().add(userRole);

        PlatformLoginRequest loginReq = new PlatformLoginRequest();
        loginReq.setUsernameOrEmail(platformAdmin.getUsername());
        loginReq.setPassword(defaultPassword);

        String loginResp = mvc.perform(post("/api/v1/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        platformToken = mapper.readTree(loginResp).path("data").path("accessToken").asText();
    }

    @Test
    @DisplayName("Complete Tenant Admin Invitation to Login and Role Isolation Flow")
    void testTenantAdminInvitationToLoginFlow() throws Exception {
        String slug = "alpha-corp-" + System.nanoTime();
        String tenantEmail = "admin@" + slug + ".local";
        String tenantAdminUsername = "alpha_admin_" + System.nanoTime();

        // 1. Platform Admin creates tenant
        CreateTenantRequest tenantReq = new CreateTenantRequest();
        tenantReq.setName("Alpha Corp");
        tenantReq.setSlug(slug);
        tenantReq.setSubscriptionPlan("ENTERPRISE");

        String tenantResp = mvc.perform(post("/api/v1/platform/tenants")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(tenantReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long tenantId = mapper.readTree(tenantResp).path("data").path("id").asLong();
        assertNotNull(tenantId);

        // 2. Platform Admin invites Tenant Admin
        CreateTenantAdminInvitationRequest invReq = new CreateTenantAdminInvitationRequest();
        invReq.setEmail(tenantEmail);

        String inviteResp = mvc.perform(post("/api/v1/platform/tenants/" + tenantId + "/admin-invitations")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(invReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long inviteId = mapper.readTree(inviteResp).path("data").path("id").asLong();
        assertNotNull(inviteId);

        TenantAdminInvitation invitation = tenantAdminInvitationRepository.findById(inviteId).orElseThrow();
        String rawToken = "raw-admin-token-" + System.nanoTime();
        invitation.setTokenHash(hashToken(rawToken));
        tenantAdminInvitationRepository.save(invitation);

        // 3. Public validates invitation token
        mvc.perform(get("/api/v1/auth/tenant-admin-invitations/validate")
                        .param("token", rawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.tenantSlug").value(slug))
                .andExpect(jsonPath("$.data.email").value(tenantEmail));

        // 4. Public accepts invitation
        AcceptTenantAdminInvitationRequest acceptReq = new AcceptTenantAdminInvitationRequest();
        acceptReq.setToken(rawToken);
        acceptReq.setUsername(tenantAdminUsername);
        acceptReq.setDisplayName("Alpha Admin");
        acceptReq.setPassword(defaultPassword);
        acceptReq.setConfirmPassword(defaultPassword);

        mvc.perform(post("/api/v1/auth/tenant-admin-invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(acceptReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginRequired").value(true))
                .andExpect(jsonPath("$.data.tenantSlug").value(slug))
                .andExpect(jsonPath("$.data.username").value(tenantAdminUsername));

        // Verify database user
        User createdUser = userRepository.findByTenantIdAndUsernameAndDeletedFalse(tenantId, tenantAdminUsername)
                .orElseThrow(() -> new AssertionError("Tenant Admin user was not found in DB under tenantId " + tenantId));

        assertEquals(User.UserStatus.ACTIVE, createdUser.getStatus());
        assertTrue(createdUser.isEmailVerified());
        assertEquals(tenantId, createdUser.getTenant().getId());

        // 5. Tenant Admin login with X-Tenant header -> SUCCESS (200)
        LoginRequest loginPayload = new LoginRequest();
        loginPayload.setTenantSlug(slug);
        loginPayload.setUsernameOrEmail(tenantAdminUsername);
        loginPayload.setPassword(defaultPassword);

        String tenantLoginResp = mvc.perform(post("/api/v1/auth/login")
                        .header("X-Tenant", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(loginPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tenantId").value(tenantId))
                .andExpect(jsonPath("$.data.roles[0]").value("TENANT_ADMIN"))
                .andReturn().getResponse().getContentAsString();

        String tenantAdminToken = mapper.readTree(tenantLoginResp).path("data").path("accessToken").asText();

        // 6. Inspect JWT claims
        assertEquals("TENANT", jwtTokenProvider.extractClaim(tenantAdminToken, claims -> claims.get("scope", String.class)));
        assertEquals(tenantId, jwtTokenProvider.extractTenantId(tenantAdminToken));

        // 7. Login without X-Tenant header (resolves to default tenant partition) -> 401 Unauthorized
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(loginPayload)))
                .andExpect(status().isUnauthorized());

        // 8. Tenant Admin cannot access Platform Admin endpoints -> 403 Forbidden
        mvc.perform(get("/api/v1/platform/tenants")
                        .header("X-Tenant", slug)
                        .header("Authorization", "Bearer " + tenantAdminToken))
                .andExpect(status().isForbidden());

        // 9. Platform Admin cannot access Tenant Admin staff endpoints -> 403 Forbidden
        mvc.perform(get("/api/v1/admin/staff/moderators")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isForbidden());

        // 10. Deactivate tenant -> subsequent login is rejected (tenant inactive)
        mvc.perform(delete("/api/v1/platform/tenants/" + tenantId)
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/login")
                        .header("X-Tenant", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(loginPayload)))
                .andExpect(status().isUnauthorized());
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
