package com.forumx.platform;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Date;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forumx.ForumXApplication;
import com.forumx.auth.dto.request.LoginRequest;
import com.forumx.auth.dto.request.RefreshTokenRequest;
import com.forumx.auth.entity.Role;
import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserRole;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.repository.RoleRepository;
import com.forumx.auth.repository.UserRepository;
import com.forumx.auth.repository.UserRoleRepository;
import com.forumx.auth.service.AccountScopeValidator;
import com.forumx.platform.auth.dto.PlatformLoginRequest;
import com.forumx.security.jwt.JwtClaimsConstants;
import com.forumx.security.jwt.JwtProperties;
import com.forumx.security.jwt.JwtTokenProvider;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
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
class PlatformIdentityIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserRepository users;
    @Autowired private UserRoleRepository userRoles;
    @Autowired private RoleRepository roles;
    @Autowired private TenantRepository tenants;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JwtTokenProvider jwt;
    @Autowired private JwtProperties jwtProperties;
    @Autowired private AccountScopeValidator scopes;

    private User platformAdmin;
    private Tenant tenantA;
    private Tenant tenantB;
    private String password = "PlatformPass123!";

    @BeforeEach
    void setup() {
        tenantA = tenants.save(Tenant.builder()
                .name("Platform Test A")
                .slug("platform-test-a-" + System.nanoTime())
                .status(Tenant.TenantStatus.ACTIVE)
                .subscriptionPlan(Tenant.SubscriptionPlan.FREE)
                .maxUsers(100)
                .storageQuotaMB(1024L)
                .timezone("UTC")
                .locale("en_US")
                .build());

        tenantB = tenants.save(Tenant.builder()
                .name("Platform Test B")
                .slug("platform-test-b-" + System.nanoTime())
                .status(Tenant.TenantStatus.ACTIVE)
                .subscriptionPlan(Tenant.SubscriptionPlan.FREE)
                .maxUsers(100)
                .storageQuotaMB(1024L)
                .timezone("UTC")
                .locale("en_US")
                .build());

        Role platformRole = roles.findByRoleName(RoleType.PLATFORM_ADMIN).orElseGet(() ->
                roles.save(Role.builder().roleName(RoleType.PLATFORM_ADMIN).active(true).build()));

        platformAdmin = users.save(User.builder()
                .username("platform_it_" + System.nanoTime())
                .email("platform_it_" + System.nanoTime() + "@forumx.local")
                .passwordHash(encoder.encode(password))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build());

        UserRole userRole = userRoles.save(UserRole.builder().user(platformAdmin).role(platformRole).active(true).build());
        platformAdmin.getUserRoles().add(userRole);
    }

    // ── 1. ACCOUNT SCOPE BOUNDARIES ──────────────────────────────────────

    @Test
    void scopeValidatorEnforcesPlatformAndTenantBoundaries() {
        assertDoesNotThrow(() -> scopes.validate(RoleType.PLATFORM_ADMIN, null));
        assertThrows(IllegalArgumentException.class, () -> scopes.validate(RoleType.PLATFORM_ADMIN, tenantA));

        assertDoesNotThrow(() -> scopes.validate(RoleType.USER, tenantA));
        assertThrows(IllegalArgumentException.class, () -> scopes.validate(RoleType.USER, null));

        assertDoesNotThrow(() -> scopes.validate(RoleType.MODERATOR, tenantA));
        assertThrows(IllegalArgumentException.class, () -> scopes.validate(RoleType.MODERATOR, null));

        assertDoesNotThrow(() -> scopes.validate(RoleType.TENANT_ADMIN, tenantA));
        assertThrows(IllegalArgumentException.class, () -> scopes.validate(RoleType.TENANT_ADMIN, null));
    }

    // ── 2. TENANT LOGIN ─────────────────────────────────────────────────

    @Test
    void tenantUsersAuthenticateOnlyThroughTenantLogin() throws Exception {
        User user = createTenantUser(tenantA, "tenant_user_a", RoleType.USER);
        User moderator = createTenantUser(tenantA, "tenant_mod_a", RoleType.MODERATOR);
        User admin = createTenantUser(tenantA, "tenant_admin_a", RoleType.TENANT_ADMIN);

        for (User candidate : new User[]{user, moderator, admin}) {
            LoginRequest login = LoginRequest.builder()
                    .tenantSlug(tenantA.getSlug())
                    .usernameOrEmail(candidate.getUsername())
                    .password(password)
                    .build();

            String response = mvc.perform(post("/api/v1/auth/login")
                            .header("X-Tenant", tenantA.getSlug())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(login)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            JsonNode data = mapper.readTree(response).path("data");
            String token = data.path("accessToken").asText();

            assertEquals("TENANT", jwt.extractClaim(token, c -> c.get("scope", String.class)));
            assertEquals(tenantA.getId(), jwt.extractTenantId(token));
            assertEquals(tenantA.getSlug(), jwt.extractClaim(token, c -> c.get("tenant_slug", String.class)));

            // Tenant token accessing platform route -> 403 Forbidden
            mvc.perform(get("/api/v1/platform/me")
                            .header("Authorization", "Bearer " + token)
                            .header("X-Tenant", tenantA.getSlug()))
                    .andExpect(status().isForbidden());

            // Tenant user attempting platform login -> 401 Unauthorized
            mvc.perform(post("/api/v1/platform/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(platformRequest(candidate.getUsername()))))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── 3. PLATFORM LOGIN ───────────────────────────────────────────────

    @Test
    void platformLoginAndIdentityDoNotRequireTenant() throws Exception {
        PlatformLoginRequest login = platformRequest(platformAdmin.getUsername());
        String response = mvc.perform(post("/api/v1/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = mapper.readTree(response).path("data");
        String token = data.path("accessToken").asText();

        assertFalse(token.isBlank());
        assertEquals("PLATFORM", jwt.extractClaim(token, c -> c.get("scope", String.class)));
        assertNull(jwt.extractClaim(token, c -> c.get("tenant_id")));
        assertNull(jwt.extractClaim(token, c -> c.get("tenant_slug")));
        assertTrue(jwt.extractRoles(token).contains("PLATFORM_ADMIN"));

        // Platform GET /me -> 200 OK
        mvc.perform(get("/api/v1/platform/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Unauthenticated -> 401 Unauthorized
        mvc.perform(get("/api/v1/platform/me")).andExpect(status().isUnauthorized());
    }

    // ── 4. CROSS-SCOPE LOGIN ─────────────────────────────────────────────

    @Test
    void crossScopeLoginIsRejected() throws Exception {
        // PLATFORM_ADMIN attempting tenant login with tenant header
        LoginRequest tenantLogin = LoginRequest.builder()
                .tenantSlug(tenantA.getSlug())
                .usernameOrEmail(platformAdmin.getUsername())
                .password(password)
                .build();

        mvc.perform(post("/api/v1/auth/login")
                        .header("X-Tenant", tenantA.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(tenantLogin)))
                .andExpect(status().isUnauthorized());

        // Tenant users attempting platform login
        User user = createTenantUser(tenantA, "cross_user_a", RoleType.USER);
        User moderator = createTenantUser(tenantA, "cross_mod_a", RoleType.MODERATOR);
        User tenantAdmin = createTenantUser(tenantA, "cross_admin_a", RoleType.TENANT_ADMIN);

        for (User candidate : new User[]{user, moderator, tenantAdmin}) {
            mvc.perform(post("/api/v1/platform/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(platformRequest(candidate.getUsername()))))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── 5. PLATFORM ROUTE AUTHORIZATION ───────────────────────────────

    @Test
    void platformRouteAuthorizationRestrictedToPlatformAdmin() throws Exception {
        User user = createTenantUser(tenantA, "route_user_a", RoleType.USER);
        User moderator = createTenantUser(tenantA, "route_mod_a", RoleType.MODERATOR);
        User tenantAdmin = createTenantUser(tenantA, "route_admin_a", RoleType.TENANT_ADMIN);

        String userToken = obtainTenantToken(tenantA, user);
        String modToken = obtainTenantToken(tenantA, moderator);
        String adminToken = obtainTenantToken(tenantA, tenantAdmin);

        for (String token : new String[]{userToken, modToken, adminToken}) {
            mvc.perform(get("/api/v1/platform/me")
                            .header("Authorization", "Bearer " + token)
                            .header("X-Tenant", tenantA.getSlug()))
                    .andExpect(status().isForbidden());
        }

        mvc.perform(get("/api/v1/platform/me")).andExpect(status().isUnauthorized());
    }

    // ── 6, 7, 8. PLATFORM TOKEN ON TENANT ENDPOINTS ───────────────────

    @Test
    void platformTokenDeniedOnTenantOperationalEndpoints() throws Exception {
        String platformToken = obtainPlatformToken(platformAdmin);

        // Tenant Staff Management endpoint -> 403
        mvc.perform(get("/api/v1/admin/staff/moderators")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isForbidden());

        // Moderation Queue endpoint -> 403
        mvc.perform(get("/api/v1/moderation/reports")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isForbidden());

        // Support Dashboard endpoint -> 403
        mvc.perform(get("/api/v1/support/tickets/dashboard/summary")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isForbidden());
    }

    // ── 9. TENANT ISOLATION ────────────────────────────────────────────

    @Test
    void tenantIsolationEnforced() throws Exception {
        User userA = createTenantUser(tenantA, "tenant_user_iso_a", RoleType.USER);
        String tokenA = obtainTenantToken(tenantA, userA);

        // Request with Tenant B header using Tenant A token -> 401 Bad Credentials / Tenant Mismatch
        mvc.perform(get("/api/v1/support/tickets")
                        .header("Authorization", "Bearer " + tokenA)
                        .header("X-Tenant", tenantB.getSlug()))
                .andExpect(status().isUnauthorized());
    }

    // ── 10. REFRESH SCOPE PRESERVATION ─────────────────────────────────

    @Test
    void refreshPreservesScope() throws Exception {
        // Platform login -> refresh
        PlatformLoginRequest platformLogin = platformRequest(platformAdmin.getUsername());
        String platformResp = mvc.perform(post("/api/v1/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(platformLogin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String platformRefreshToken = mapper.readTree(platformResp).path("data").path("refreshToken").asText();

        RefreshTokenRequest refReq = new RefreshTokenRequest();
        refReq.setRefreshToken(platformRefreshToken);

        String refResp = mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(refReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String newPlatformToken = mapper.readTree(refResp).path("data").path("accessToken").asText();
        assertEquals("PLATFORM", jwt.extractClaim(newPlatformToken, c -> c.get("scope", String.class)));
        assertNull(jwt.extractClaim(newPlatformToken, c -> c.get("tenant_id")));

        // Tenant login -> refresh
        User tenantUser = createTenantUser(tenantA, "ref_user_a", RoleType.USER);
        LoginRequest tenantLogin = LoginRequest.builder()
                .tenantSlug(tenantA.getSlug())
                .usernameOrEmail(tenantUser.getUsername())
                .password(password)
                .build();

        String tenantResp = mvc.perform(post("/api/v1/auth/login")
                        .header("X-Tenant", tenantA.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(tenantLogin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String tenantRefreshToken = mapper.readTree(tenantResp).path("data").path("refreshToken").asText();

        refReq.setRefreshToken(tenantRefreshToken);

        String refRespTenant = mvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Tenant", tenantA.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(refReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String newTenantToken = mapper.readTree(refRespTenant).path("data").path("accessToken").asText();
        assertEquals("TENANT", jwt.extractClaim(newTenantToken, c -> c.get("scope", String.class)));
        assertEquals(tenantA.getId(), jwt.extractTenantId(newTenantToken));
    }

    // ── 11. MALFORMED SCOPE DEFENSE ───────────────────────────────────

    @Test
    void malformedScopeCombinationsRejected() throws Exception {
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecret());

        // 1. PLATFORM scope with tenant_id claim
        String malformedPlatform = Jwts.builder()
                .subject(platformAdmin.getUsername())
                .claim(JwtClaimsConstants.SCOPE, "PLATFORM")
                .claim(JwtClaimsConstants.TENANT_ID, 999L)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 600000))
                .signWith(Keys.hmacShaKeyFor(keyBytes))
                .compact();

        mvc.perform(get("/api/v1/platform/me")
                        .header("Authorization", "Bearer " + malformedPlatform))
                .andExpect(status().isUnauthorized());

        // 2. TENANT scope without tenant_id claim
        String malformedTenant = Jwts.builder()
                .subject("tenant_user_a")
                .claim(JwtClaimsConstants.SCOPE, "TENANT")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 600000))
                .signWith(Keys.hmacShaKeyFor(keyBytes))
                .compact();

        mvc.perform(get("/api/v1/support/tickets")
                        .header("Authorization", "Bearer " + malformedTenant)
                        .header("X-Tenant", tenantA.getSlug()))
                .andExpect(status().isUnauthorized());
    }

    // ── HELPERS ─────────────────────────────────────────────────────────

    private User createTenantUser(Tenant tenant, String username, RoleType type) {
        Role role = roles.findByRoleName(type).orElseGet(() -> roles.save(Role.builder().roleName(type).active(true).build()));
        User user = users.save(User.builder()
                .tenant(tenant)
                .username(username + "_" + System.nanoTime())
                .email(username + "_" + System.nanoTime() + "@forumx.local")
                .passwordHash(encoder.encode(password))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build());
        userRoles.save(UserRole.builder().user(user).role(role).active(true).build());
        return user;
    }

    private PlatformLoginRequest platformRequest(String username) {
        PlatformLoginRequest request = new PlatformLoginRequest();
        request.setUsernameOrEmail(username);
        request.setPassword(password);
        return request;
    }

    private String obtainPlatformToken(User admin) throws Exception {
        PlatformLoginRequest login = platformRequest(admin.getUsername());
        String response = mvc.perform(post("/api/v1/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return mapper.readTree(response).path("data").path("accessToken").asText();
    }

    private String obtainTenantToken(Tenant tenant, User user) throws Exception {
        LoginRequest login = LoginRequest.builder()
                .tenantSlug(tenant.getSlug())
                .usernameOrEmail(user.getUsername())
                .password(password)
                .build();

        String response = mvc.perform(post("/api/v1/auth/login")
                        .header("X-Tenant", tenant.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return mapper.readTree(response).path("data").path("accessToken").asText();
    }
}
