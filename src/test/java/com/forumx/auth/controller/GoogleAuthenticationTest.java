package com.forumx.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forumx.auth.dto.request.ForgotPasswordRequest;
import com.forumx.auth.dto.request.GoogleLoginRequest;
import com.forumx.auth.dto.request.LoginRequest;
import com.forumx.auth.dto.request.ResetPasswordRequest;
import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserProfile;
import com.forumx.auth.passwordreset.repository.PasswordResetTokenRepository;
import com.forumx.auth.repository.UserProfileRepository;
import com.forumx.auth.repository.UserRepository;
import com.forumx.auth.service.GoogleTokenVerifierService;
import com.forumx.mail.EmailService;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;



import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
public class GoogleAuthenticationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private TenantRepository tenantRepository;



    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GoogleTokenVerifierService googleTokenVerifierService;

    @MockBean
    private EmailService emailService;

    private Tenant defaultTenant;

    @BeforeEach
    void setUp() {
        // Ensure default tenant exists
        defaultTenant = tenantRepository.findBySlug("default")
                .orElseGet(() -> tenantRepository.save(
                        Tenant.builder()
                                .name("Default Tenant")
                                .slug("default")
                                .status(Tenant.TenantStatus.ACTIVE)
                                .build()
                ));
    }

    @Test
    void testGoogleLoginNewUserCreatesAccount() throws Exception {
        // Mock token claims
        GoogleTokenVerifierService.GoogleClaims claims = new GoogleTokenVerifierService.GoogleClaims(
                "google-sub-new-123",
                "newuser@example.com",
                "New",
                "User",
                "New User",
                "https://avatar.url/newuser"
        );
        when(googleTokenVerifierService.verify("valid-token")).thenReturn(claims);

        GoogleLoginRequest request = GoogleLoginRequest.builder()
                .idToken("valid-token")
                .tenantSlug("default")
                .build();

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.email").value("newuser@example.com"));

        // Verify database state
        User createdUser = userRepository.findByTenantIdAndEmailAndDeletedFalse(defaultTenant.getId(), "newuser@example.com")
                .orElseThrow(() -> new AssertionError("User not created"));

        assertNull(createdUser.getPasswordHash());
        assertEquals("google-sub-new-123", createdUser.getGoogleId());
        assertTrue(createdUser.isEmailVerified());
        
        UserProfile profile = createdUser.getUserProfile();
        assertNotNull(profile);
        assertEquals("New", profile.getFirstName());
        assertEquals("User", profile.getLastName());
        assertEquals("New User", profile.getDisplayName());
        assertEquals("https://avatar.url/newuser", profile.getAvatarUrl());
    }

    @Test
    void testGoogleLoginExistingLocalUserLinksAccount() throws Exception {
        // Create local user
        User localUser = User.builder()
                .tenant(defaultTenant)
                .username("localuser")
                .email("localuser@example.com")
                .passwordHash(passwordEncoder.encode("LocalSecret123!"))
                .emailVerified(true)
                .enabled(true)
                .status(User.UserStatus.ACTIVE)
                .build();
        userRepository.saveAndFlush(localUser);

        UserProfile profile = UserProfile.builder()
                .user(localUser)
                .displayName("Local User")
                .build();
        userProfileRepository.save(profile);
        localUser.setUserProfile(profile);

        // Mock token claims
        GoogleTokenVerifierService.GoogleClaims claims = new GoogleTokenVerifierService.GoogleClaims(
                "google-sub-local-999",
                "localuser@example.com",
                "Local",
                "User",
                "Local User",
                "https://avatar.url/local"
        );
        when(googleTokenVerifierService.verify("valid-token")).thenReturn(claims);

        GoogleLoginRequest request = GoogleLoginRequest.builder()
                .idToken("valid-token")
                .tenantSlug("default")
                .build();

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("localuser@example.com"));

        // Verify linked state
        User updatedUser = userRepository.findById(localUser.getId())
                .orElseThrow();
        assertEquals("google-sub-local-999", updatedUser.getGoogleId());
        assertNotNull(updatedUser.getPasswordHash()); // Password remains intact
        assertEquals("https://avatar.url/local", updatedUser.getUserProfile().getAvatarUrl());
    }

    @Test
    void testGoogleLoginDoesNotCreateDuplicateUserForExistingLinkedAccount() throws Exception {
        // Create user already linked
        User linkedUser = User.builder()
                .tenant(defaultTenant)
                .username("linkeduser")
                .email("linkeduser@example.com")
                .googleId("google-sub-existing")
                .emailVerified(true)
                .enabled(true)
                .status(User.UserStatus.ACTIVE)
                .build();
        userRepository.saveAndFlush(linkedUser);

        long initialCount = userRepository.count();

        // Mock token claims
        GoogleTokenVerifierService.GoogleClaims claims = new GoogleTokenVerifierService.GoogleClaims(
                "google-sub-existing",
                "linkeduser@example.com",
                "Linked",
                "User",
                "Linked User",
                "https://avatar.url/linked"
        );
        when(googleTokenVerifierService.verify("valid-token")).thenReturn(claims);

        GoogleLoginRequest request = GoogleLoginRequest.builder()
                .idToken("valid-token")
                .tenantSlug("default")
                .build();

        // Log in first time
        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Log in second time
        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Assert count remains same
        assertEquals(initialCount, userRepository.count());
    }

    @Test
    void testGoogleLoginInvalidTokenRejected() throws Exception {
        when(googleTokenVerifierService.verify("invalid-token"))
                .thenThrow(new IllegalArgumentException("Invalid Google ID Token"));

        GoogleLoginRequest request = GoogleLoginRequest.builder()
                .idToken("invalid-token")
                .tenantSlug("default")
                .build();

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testGoogleLoginHijackAttemptRejected() throws Exception {
        // User A already has the googleId
        User userA = User.builder()
                .tenant(defaultTenant)
                .username("userA")
                .email("userA@example.com")
                .googleId("google-sub-shared")
                .emailVerified(true)
                .enabled(true)
                .status(User.UserStatus.ACTIVE)
                .build();
        userRepository.saveAndFlush(userA);

        // Mock token claims: returns google-sub-shared but for userB's email
        GoogleTokenVerifierService.GoogleClaims claims = new GoogleTokenVerifierService.GoogleClaims(
                "google-sub-shared",
                "userB@example.com",
                "User",
                "B",
                "User B",
                null
        );
        when(googleTokenVerifierService.verify("valid-token")).thenReturn(claims);

        GoogleLoginRequest request = GoogleLoginRequest.builder()
                .idToken("valid-token")
                .tenantSlug("default")
                .build();

        // Perform Google login for user B - should be rejected as sub is already linked to user A
        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testLocalLoginForGoogleOnlyAccountReturnsPasswordNotSet() throws Exception {
        // Create Google-only user
        User googleOnlyUser = User.builder()
                .tenant(defaultTenant)
                .username("googleonly")
                .email("googleonly@example.com")
                .passwordHash(null)
                .googleId("google-sub-only")
                .emailVerified(true)
                .enabled(true)
                .status(User.UserStatus.ACTIVE)
                .build();
        userRepository.saveAndFlush(googleOnlyUser);

        LoginRequest request = LoginRequest.builder()
                .usernameOrEmail("googleonly@example.com")
                .password("SomePassword123!")
                .tenantSlug("default")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.errorCode").value("PASSWORD_NOT_SET"));
    }

    @Test
    void testForgotPasswordAndResetPasswordForGoogleOnlyAccount() throws Exception {
        // Create Google-only user
        User googleOnlyUser = User.builder()
                .tenant(defaultTenant)
                .username("setupuser")
                .email("setupuser@example.com")
                .passwordHash(null)
                .googleId("google-sub-setup")
                .emailVerified(true)
                .enabled(true)
                .status(User.UserStatus.ACTIVE)
                .build();
        userRepository.saveAndFlush(googleOnlyUser);

        // 1. Trigger forgot password
        ForgotPasswordRequest forgotReq = ForgotPasswordRequest.builder()
                .email("setupuser@example.com")
                .build();

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotReq)))
                .andExpect(status().isOk());

        // Retrieve token from DB
        assertTrue(passwordResetTokenRepository.findAll().stream()
                .anyMatch(t -> t.getUser().getId().equals(googleOnlyUser.getId())));

        // Capture raw token using mock mail service
        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq("setupuser@example.com"), eq("setupuser"), linkCaptor.capture());
        
        String resetLink = linkCaptor.getValue();
        String rawToken = resetLink.substring(resetLink.indexOf("token=") + 6);

        // 2. Perform reset password
        ResetPasswordRequest resetReq = ResetPasswordRequest.builder()
                .token(rawToken)
                .newPassword("NewSecurePassword123!")
                .confirmPassword("NewSecurePassword123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetReq)))
                .andExpect(status().isOk());

        // 3. Confirm password hash is set
        User updatedUser = userRepository.findById(googleOnlyUser.getId()).orElseThrow();
        assertNotNull(updatedUser.getPasswordHash());
        assertTrue(passwordEncoder.matches("NewSecurePassword123!", updatedUser.getPasswordHash()));

        // 4. Confirm local login works now
        LoginRequest loginReq = LoginRequest.builder()
                .usernameOrEmail("setupuser@example.com")
                .password("NewSecurePassword123!")
                .tenantSlug("default")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 5. Confirm Google login still works
        GoogleTokenVerifierService.GoogleClaims claims = new GoogleTokenVerifierService.GoogleClaims(
                "google-sub-setup",
                "setupuser@example.com",
                "Setup",
                "User",
                "Setup User",
                null
        );
        when(googleTokenVerifierService.verify("valid-token")).thenReturn(claims);

        GoogleLoginRequest googleReq = GoogleLoginRequest.builder()
                .idToken("valid-token")
                .tenantSlug("default")
                .build();

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(googleReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
