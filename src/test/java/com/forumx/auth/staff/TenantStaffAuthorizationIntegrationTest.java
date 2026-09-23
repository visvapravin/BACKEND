package com.forumx.auth.staff;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forumx.ForumXApplication;
import com.forumx.auth.entity.Role;
import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserRole;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.invitation.dto.request.CreateInvitationRequest;
import com.forumx.auth.invitation.entity.InvitationStatus;
import com.forumx.auth.invitation.entity.ModeratorInvitation;
import com.forumx.auth.invitation.repository.ModeratorInvitationRepository;
import com.forumx.auth.repository.RoleRepository;
import com.forumx.auth.repository.UserRepository;
import com.forumx.auth.repository.UserRoleRepository;
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

import java.time.Instant;
import java.util.List;

@SpringBootTest(classes = ForumXApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
public class TenantStaffAuthorizationIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private ModeratorInvitationRepository invitationRepository;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JwtTokenProvider jwt;

    private static final String PASSWORD = "TestPassword123!";
    private Tenant tenantA;
    private Tenant tenantB;
    private User tenantAdminA;
    private User moderatorAlice;
    private User moderatorBob;
    private User platformAdmin;
    private String tokenTenantAdminA;
    private String tokenPlatformAdmin;

    @BeforeEach
    void setUp() {
        tenantA = tenantRepository.save(Tenant.builder()
                .name("Tenant Staff A " + System.nanoTime())
                .slug("tenant-staff-a-" + System.nanoTime())
                .status(Tenant.TenantStatus.ACTIVE)
                .build());

        tenantB = tenantRepository.save(Tenant.builder()
                .name("Tenant Staff B " + System.nanoTime())
                .slug("tenant-staff-b-" + System.nanoTime())
                .status(Tenant.TenantStatus.ACTIVE)
                .build());

        Role roleTenantAdmin = roleRepository.findByRoleName(RoleType.TENANT_ADMIN).orElseThrow();
        Role roleModerator = roleRepository.findByRoleName(RoleType.MODERATOR).orElseThrow();
        Role rolePlatformAdmin = roleRepository.findByRoleName(RoleType.PLATFORM_ADMIN).orElseThrow();

        // Tenant Admin in Tenant A
        tenantAdminA = userRepository.save(User.builder()
                .username("admin_a_" + System.nanoTime())
                .email("admin_a_" + System.nanoTime() + "@example.com")
                .passwordHash(encoder.encode(PASSWORD))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build());
        UserRole roleTA = userRoleRepository.save(UserRole.builder().user(tenantAdminA).tenant(tenantA).role(roleTenantAdmin).active(true).build());

        // Alice = Moderator in Tenant A
        moderatorAlice = userRepository.save(User.builder()
                .username("alice_mod_" + System.nanoTime())
                .email("alice_" + System.nanoTime() + "@example.com")
                .passwordHash(encoder.encode(PASSWORD))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(UserRole.builder().user(moderatorAlice).tenant(tenantA).role(roleModerator).active(true).build());

        // Bob = Moderator in Tenant B
        moderatorBob = userRepository.save(User.builder()
                .username("bob_mod_" + System.nanoTime())
                .email("bob_" + System.nanoTime() + "@example.com")
                .passwordHash(encoder.encode(PASSWORD))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(UserRole.builder().user(moderatorBob).tenant(tenantB).role(roleModerator).active(true).build());

        // Platform Admin (Global)
        platformAdmin = userRepository.save(User.builder()
                .username("platform_adm_" + System.nanoTime())
                .email("platform_adm_" + System.nanoTime() + "@example.com")
                .passwordHash(encoder.encode(PASSWORD))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build());
        UserRole rolePA = userRoleRepository.save(UserRole.builder().user(platformAdmin).role(rolePlatformAdmin).active(true).build());

        tokenTenantAdminA = jwt.generateAccessToken(new com.forumx.security.model.CustomUserDetails(tenantAdminA, tenantA.getId(), tenantA.getSlug(), List.of(roleTA)));
        tokenPlatformAdmin = jwt.generateAccessToken(new com.forumx.security.model.CustomUserDetails(platformAdmin, null, null, List.of(rolePA)));
    }

    @Test
    @DisplayName("TEST 3: TENANT_ADMIN in Tenant A invites moderator -> 201 Created")
    void test3_tenantAdminInvitesModerator_success() throws Exception {
        CreateInvitationRequest req = CreateInvitationRequest.builder()
                .email("new_mod_" + System.nanoTime() + "@example.com")
                .build();

        mvc.perform(post("/api/v1/admin/staff/invitations")
                        .header("Authorization", "Bearer " + tokenTenantAdminA)
                        .header("X-Tenant", tenantA.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(req.getEmail().toLowerCase()))
                .andExpect(jsonPath("$.data.tenantId").value(tenantA.getId()));
    }

    @Test
    @DisplayName("TEST 4: TENANT_ADMIN in Tenant A attempts operation against Tenant B -> 403 Forbidden")
    void test4_tenantAdminCrossTenantOperation_forbidden() throws Exception {
        CreateInvitationRequest req = CreateInvitationRequest.builder()
                .email("cross_tenant_mod_" + System.nanoTime() + "@example.com")
                .build();

        // Sending Token A with Tenant B header -> 401 Unauthorized (rejected by JwtAuthenticationFilter)
        mvc.perform(post("/api/v1/admin/staff/invitations")
                        .header("Authorization", "Bearer " + tokenTenantAdminA)
                        .header("X-Tenant", tenantB.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TEST 5: PLATFORM_ADMIN attempts POST /admin/staff/invitations -> 403 Forbidden")
    void test5_platformAdminStaffInvitation_forbidden() throws Exception {
        CreateInvitationRequest req = CreateInvitationRequest.builder()
                .email("platform_mod_" + System.nanoTime() + "@example.com")
                .build();

        mvc.perform(post("/api/v1/admin/staff/invitations")
                        .header("Authorization", "Bearer " + tokenPlatformAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TEST 6: TENANT_ADMIN in Tenant A lists moderators -> 200 OK with sorting")
    void test6_tenantAdminListsModerators_success() throws Exception {
        mvc.perform(get("/api/v1/admin/staff/moderators?size=50&sort=username,asc")
                        .header("Authorization", "Bearer " + tokenTenantAdminA)
                        .header("X-Tenant", tenantA.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("TEST 7: PLATFORM_ADMIN attempts GET /admin/staff/moderators -> 403 Forbidden")
    void test7_platformAdminListModerators_forbidden() throws Exception {
        mvc.perform(get("/api/v1/admin/staff/moderators")
                        .header("Authorization", "Bearer " + tokenPlatformAdmin))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TEST 8: Tenant A lists Alice only, Bob does not appear")
    void test8_tenantIsolationInModeratorList() throws Exception {
        mvc.perform(get("/api/v1/admin/staff/moderators")
                        .header("Authorization", "Bearer " + tokenTenantAdminA)
                        .header("X-Tenant", tenantA.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].username").value(moderatorAlice.getUsername()))
                .andExpect(jsonPath("$.data.content[1]").doesNotExist());
    }

    @Test
    @DisplayName("TEST 11: Expired invitation allows re-inviting with a new invitation")
    void test11_expiredInvitation_canBeReissued() throws Exception {
        String email = "reissue_mod_" + System.nanoTime() + "@example.com";

        // Old expired invitation exists
        invitationRepository.save(ModeratorInvitation.builder()
                .tenant(tenantA)
                .invitedBy(tenantAdminA)
                .email(email)
                .role(RoleType.MODERATOR)
                .tokenHash("old_expired_hash")
                .status(InvitationStatus.EXPIRED)
                .expiresAt(Instant.now().minusSeconds(3600))
                .build());

        CreateInvitationRequest req = CreateInvitationRequest.builder()
                .email(email)
                .build();

        mvc.perform(post("/api/v1/admin/staff/invitations")
                        .header("Authorization", "Bearer " + tokenTenantAdminA)
                        .header("X-Tenant", tenantA.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(email));
    }
}
