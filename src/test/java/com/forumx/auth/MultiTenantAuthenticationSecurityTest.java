package com.forumx.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forumx.ForumXApplication;
import com.forumx.auth.dto.request.GoogleLoginRequest;
import com.forumx.auth.dto.request.LoginRequest;
import com.forumx.auth.dto.request.RefreshTokenRequest;
import com.forumx.auth.dto.request.RegisterRequest;
import com.forumx.auth.entity.Role;
import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserRole;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.repository.RoleRepository;
import com.forumx.auth.repository.UserRepository;
import com.forumx.auth.repository.UserRoleRepository;
import com.forumx.auth.service.GoogleTokenVerifierService;
import com.forumx.security.jwt.JwtTokenProvider;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = ForumXApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class MultiTenantAuthenticationSecurityTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JwtTokenProvider jwt;

    @MockBean
    private GoogleTokenVerifierService googleTokenVerifierService;

    private static final String PASSWORD = "SecPassword123!";
    private Tenant tenantDefault;
    private Tenant tenantKrct;
    private Tenant tenantAbc;
    private Role userRole;

    @BeforeEach
    void setUp() {
        tenantDefault = tenantRepository.findBySlugAndDeletedFalse("default")
                .orElseGet(() -> tenantRepository.save(Tenant.builder()
                        .name("Default Workspace")
                        .slug("default")
                        .status(Tenant.TenantStatus.ACTIVE)
                        .deleted(false)
                        .build()));

        tenantKrct = tenantRepository.save(Tenant.builder()
                .name("KRCT Workspace " + System.nanoTime())
                .slug("krct-" + System.nanoTime())
                .status(Tenant.TenantStatus.ACTIVE)
                .deleted(false)
                .build());

        tenantAbc = tenantRepository.save(Tenant.builder()
                .name("ABC Workspace " + System.nanoTime())
                .slug("abc-" + System.nanoTime())
                .status(Tenant.TenantStatus.ACTIVE)
                .deleted(false)
                .build());

        userRole = roleRepository.findByRoleName(RoleType.USER)
                .orElseThrow(() -> new IllegalStateException("ROLE_USER not found"));
    }

    @Test
    @DisplayName("CASE 1 & 2: User belongs ONLY to DEFAULT -> Login to DEFAULT succeeds, login to KRCT is DENIED")
    void testCase1And2_userInDefault_cannotLoginToKrct() throws Exception {
        String email = "alice_" + System.nanoTime() + "@forumx.local";
        User user = userRepository.save(User.builder()
                .tenant(null)
                .username("alice_" + System.nanoTime())
                .email(email)
                .passwordHash(encoder.encode(PASSWORD))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build());

        // Assign membership ONLY in tenantDefault
        userRoleRepository.save(UserRole.builder()
                .user(user)
                .tenant(tenantDefault)
                .role(userRole)
                .active(true)
                .build());

        // 1. Login to DEFAULT -> SUCCESS
        LoginRequest defaultLogin = new LoginRequest();
        defaultLogin.setUsernameOrEmail(email);
        defaultLogin.setPassword(PASSWORD);
        defaultLogin.setTenantSlug(tenantDefault.getSlug());

        MvcResult defaultResult = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(defaultLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tenantId").value(tenantDefault.getId()))
                .andExpect(jsonPath("$.data.tenantSlug").value(tenantDefault.getSlug()))
                .andReturn();

        String accessToken = mapper.readTree(defaultResult.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
        assertEquals(tenantDefault.getId(), jwt.extractTenantId(accessToken));

        // 2. Login to KRCT -> DENIED (401 Bad Credentials)
        LoginRequest krctLogin = new LoginRequest();
        krctLogin.setUsernameOrEmail(email);
        krctLogin.setPassword(PASSWORD);
        krctLogin.setTenantSlug(tenantKrct.getSlug());

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(krctLogin)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CASE 3: User belongs to DEFAULT and KRCT -> Both logins succeed with distinct tenant JWTs")
    void testCase3_userInBothTenants_getsAppropriateTenantTokens() throws Exception {
        String email = "bob_" + System.nanoTime() + "@forumx.local";
        User user = userRepository.save(User.builder()
                .tenant(null)
                .username("bob_" + System.nanoTime())
                .email(email)
                .passwordHash(encoder.encode(PASSWORD))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build());

        // Assign memberships in BOTH DEFAULT and KRCT
        userRoleRepository.save(UserRole.builder()
                .user(user)
                .tenant(tenantDefault)
                .role(userRole)
                .active(true)
                .build());

        userRoleRepository.save(UserRole.builder()
                .user(user)
                .tenant(tenantKrct)
                .role(userRole)
                .active(true)
                .build());

        // Login to DEFAULT
        LoginRequest defaultLogin = new LoginRequest();
        defaultLogin.setUsernameOrEmail(email);
        defaultLogin.setPassword(PASSWORD);
        defaultLogin.setTenantSlug(tenantDefault.getSlug());

        MvcResult defaultRes = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(defaultLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantSlug").value(tenantDefault.getSlug()))
                .andReturn();

        String defaultToken = mapper.readTree(defaultRes.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
        assertEquals(tenantDefault.getId(), jwt.extractTenantId(defaultToken));

        // Login to KRCT
        LoginRequest krctLogin = new LoginRequest();
        krctLogin.setUsernameOrEmail(email);
        krctLogin.setPassword(PASSWORD);
        krctLogin.setTenantSlug(tenantKrct.getSlug());

        MvcResult krctRes = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(krctLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantSlug").value(tenantKrct.getSlug()))
                .andReturn();

        String krctToken = mapper.readTree(krctRes.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
        assertEquals(tenantKrct.getId(), jwt.extractTenantId(krctToken));

        // Login to ABC (no membership) -> DENIED
        LoginRequest abcLogin = new LoginRequest();
        abcLogin.setUsernameOrEmail(email);
        abcLogin.setPassword(PASSWORD);
        abcLogin.setTenantSlug(tenantAbc.getSlug());

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(abcLogin)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CASE 4 & 5: Google Login validates membership; does NOT auto-grant roles on login")
    void testCase4And5_googleLogin_doesNotAutoGrantMembership() throws Exception {
        String googleSub = "google_sub_" + System.nanoTime();
        String googleEmail = "googleuser_" + System.nanoTime() + "@forumx.local";

        when(googleTokenVerifierService.verify(eq("valid_token_123")))
                .thenReturn(new GoogleTokenVerifierService.GoogleClaims(
                        googleSub,
                        googleEmail,
                        "Google",
                        "User",
                        "Google User",
                        "https://avatar.example/photo.png"
                ));

        // Create global user with membership ONLY in tenantDefault
        User user = userRepository.save(User.builder()
                .tenant(null)
                .username("guser_" + System.nanoTime())
                .email(googleEmail)
                .googleId(googleSub)
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build());

        userRoleRepository.save(UserRole.builder()
                .user(user)
                .tenant(tenantDefault)
                .role(userRole)
                .active(true)
                .build());

        // Google login to tenantDefault -> SUCCESS
        GoogleLoginRequest defaultGoogleLogin = new GoogleLoginRequest();
        defaultGoogleLogin.setIdToken("valid_token_123");
        defaultGoogleLogin.setTenantSlug(tenantDefault.getSlug());

        mvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(defaultGoogleLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantSlug").value(tenantDefault.getSlug()));

        // Google login to KRCT -> DENIED (No auto-grant of membership!)
        GoogleLoginRequest krctGoogleLogin = new GoogleLoginRequest();
        krctGoogleLogin.setIdToken("valid_token_123");
        krctGoogleLogin.setTenantSlug(tenantKrct.getSlug());

        mvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(krctGoogleLogin)))
                .andExpect(status().isUnauthorized());

        // Verify NO UserRole was created in tenantKrct
        assertFalse(userRoleRepository.existsActiveMembership(user.getId(), tenantKrct.getId()));
    }

    @Test
    @DisplayName("CASE 6: Existing global user registers at /krct/signup -> Joins KRCT without duplicate global user")
    void testCase6_existingUserRegistersInNewTenant_createsMembership() throws Exception {
        String email = "shared_" + System.nanoTime() + "@forumx.local";
        User user = userRepository.save(User.builder()
                .tenant(null)
                .username("shared_" + System.nanoTime())
                .email(email)
                .passwordHash(encoder.encode(PASSWORD))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build());

        userRoleRepository.save(UserRole.builder()
                .user(user)
                .tenant(tenantDefault)
                .role(userRole)
                .active(true)
                .build());

        long initialUserCount = userRepository.count();

        // Register for KRCT
        RegisterRequest registerReq = new RegisterRequest();
        registerReq.setUsername(user.getUsername());
        registerReq.setEmail(email);
        registerReq.setPassword(PASSWORD);
        registerReq.setConfirmPassword(PASSWORD);
        registerReq.setTenantSlug(tenantKrct.getSlug());

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        // Verify no duplicate global User record created
        assertEquals(initialUserCount, userRepository.count());

        // Verify user now has active membership in KRCT
        assertTrue(userRoleRepository.existsActiveMembership(user.getId(), tenantKrct.getId()));

        // User can now log into KRCT
        LoginRequest krctLogin = new LoginRequest();
        krctLogin.setUsernameOrEmail(email);
        krctLogin.setPassword(PASSWORD);
        krctLogin.setTenantSlug(tenantKrct.getSlug());

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(krctLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantSlug").value(tenantKrct.getSlug()));
    }

    @Test
    @DisplayName("CASE 7 & 8: Cross-tenant token replay is rejected by tenant authorization")
    void testCase7And8_tenantMismatchReplay_isForbidden() throws Exception {
        String email = "charlie_" + System.nanoTime() + "@forumx.local";
        User user = userRepository.save(User.builder()
                .tenant(null)
                .username("charlie_" + System.nanoTime())
                .email(email)
                .passwordHash(encoder.encode(PASSWORD))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build());

        userRoleRepository.save(UserRole.builder()
                .user(user)
                .tenant(tenantDefault)
                .role(userRole)
                .active(true)
                .build());

        LoginRequest loginReq = new LoginRequest();
        loginReq.setUsernameOrEmail(email);
        loginReq.setPassword(PASSWORD);
        loginReq.setTenantSlug(tenantDefault.getSlug());

        MvcResult loginRes = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String defaultAccessToken = mapper.readTree(loginRes.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // Using DEFAULT access token with X-Tenant: KRCT header -> 401 Unauthorized (rejected by JwtAuthenticationFilter tenant verification)
        mvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + defaultAccessToken)
                        .header("X-Tenant", tenantKrct.getSlug()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CASE 9: Refresh Token remains scoped to its original tenant")
    void testCase9_refreshToken_remainsScopedToOriginalTenant() throws Exception {
        String email = "dan_" + System.nanoTime() + "@forumx.local";
        User user = userRepository.save(User.builder()
                .tenant(null)
                .username("dan_" + System.nanoTime())
                .email(email)
                .passwordHash(encoder.encode(PASSWORD))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build());

        userRoleRepository.save(UserRole.builder()
                .user(user)
                .tenant(tenantDefault)
                .role(userRole)
                .active(true)
                .build());

        LoginRequest loginReq = new LoginRequest();
        loginReq.setUsernameOrEmail(email);
        loginReq.setPassword(PASSWORD);
        loginReq.setTenantSlug(tenantDefault.getSlug());

        MvcResult loginRes = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken = mapper.readTree(loginRes.getResponse().getContentAsString())
                .path("data").path("refreshToken").asText();

        // Refresh token
        RefreshTokenRequest refreshReq = new RefreshTokenRequest();
        refreshReq.setRefreshToken(refreshToken);

        MvcResult refreshRes = mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andReturn();

        String newAccessToken = mapper.readTree(refreshRes.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
        assertEquals(tenantDefault.getId(), jwt.extractTenantId(newAccessToken));
    }
}
