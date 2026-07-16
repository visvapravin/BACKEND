package com.forumx.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forumx.auth.dto.request.LoginRequest;
import com.forumx.auth.dto.request.RegisterRequest;
import com.forumx.auth.dto.request.ResendVerificationRequest;
import com.forumx.auth.entity.User;
import com.forumx.auth.repository.UserRepository;
import com.forumx.auth.verification.entity.VerificationToken;
import com.forumx.auth.verification.repository.VerificationTokenRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
public class EmailVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmailService emailService;

    private Tenant defaultTenant;

    @BeforeEach
    public void setUp() {
        defaultTenant = tenantRepository.findBySlug("default")
                .orElseThrow(() -> new IllegalStateException("Default tenant not found. Please ensure seed runs."));
        doNothing().when(emailService).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    public void testRegisterGeneratesHashedVerificationTokenAndCallsEmailService() throws Exception {
        String username = "testuser_" + System.currentTimeMillis();
        String email = username + "@example.com";

        RegisterRequest request = RegisterRequest.builder()
                .tenantSlug("default")
                .username(username)
                .email(email)
                .password("SecurePassword123!")
                .confirmPassword("SecurePassword123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Registration successful. Please verify your email."));

        // Verify user is in DB and email_verified = false
        User user = userRepository.findByTenantIdAndUsernameAndDeletedFalse(defaultTenant.getId(), username)
                .orElseThrow();
        assertFalse(user.isEmailVerified());

        // Verify VerificationToken is generated
        List<VerificationToken> tokens = verificationTokenRepository.findAllByUserAndUsedFalse(user);
        assertEquals(1, tokens.size());
        
        VerificationToken token = tokens.get(0);
        assertNotNull(token.getTokenHash());
        assertEquals(64, token.getTokenHash().length()); // SHA-256 hex is 64 characters
        assertFalse(token.isUsed());

        // Verify EmailService was called with the correct raw token link
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendVerificationEmail(eq(email), eq(username), urlCaptor.capture());

        String verificationUrl = urlCaptor.getValue();
        assertTrue(verificationUrl.startsWith("http://localhost:3000/verify-email?token="));
    }

    @Test
    public void testLoginFailsBeforeVerification() throws Exception {
        String username = "unverified_" + System.currentTimeMillis();
        String email = username + "@example.com";
        String password = "SecurePassword123!";

        User user = User.builder()
                .tenant(defaultTenant)
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .emailVerified(false)
                .enabled(true)
                .status(User.UserStatus.ACTIVE)
                .build();
        userRepository.saveAndFlush(user);

        LoginRequest request = LoginRequest.builder()
                .tenantSlug("default")
                .usernameOrEmail(username)
                .password(password)
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Tenant", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.errorCode").value("EMAIL_NOT_VERIFIED"))
                .andExpect(jsonPath("$.message").value("Please verify your email before logging in."));
    }

    @Test
    public void testVerifyEmailSuccess() throws Exception {
        String username = "verify_" + System.currentTimeMillis();
        String email = username + "@example.com";

        User user = User.builder()
                .tenant(defaultTenant)
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode("password123"))
                .emailVerified(false)
                .enabled(true)
                .status(User.UserStatus.ACTIVE)
                .build();
        userRepository.saveAndFlush(user);

        // Generate and save token
        String rawToken = "rawTokenSequenceForVerifyEmailSuccess";
        // Computes SHA-256 hex hash
        String tokenHash = hashToken(rawToken);

        VerificationToken verificationToken = VerificationToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .used(false)
                .build();
        verificationTokenRepository.saveAndFlush(verificationToken);

        mockMvc.perform(get("/api/v1/auth/verify-email")
                        .param("token", rawToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Email verified successfully"));

        // Assert DB changes
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertTrue(updatedUser.isEmailVerified());

        VerificationToken updatedToken = verificationTokenRepository.findById(verificationToken.getId()).orElseThrow();
        assertTrue(updatedToken.isUsed());
        assertNotNull(updatedToken.getUsedAt());
    }

    @Test
    public void testLoginSucceedsAfterVerification() throws Exception {
        String username = "verified_" + System.currentTimeMillis();
        String email = username + "@example.com";
        String password = "SecurePassword123!";

        User user = User.builder()
                .tenant(defaultTenant)
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .emailVerified(true)
                .enabled(true)
                .status(User.UserStatus.ACTIVE)
                .build();
        userRepository.saveAndFlush(user);

        LoginRequest request = LoginRequest.builder()
                .tenantSlug("default")
                .usernameOrEmail(username)
                .password(password)
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Tenant", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists());
    }

    @Test
    public void testVerifyEmailInvalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/verify-email")
                        .param("token", "nonexistentRawTokenString")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.errorCode").value("TOKEN_INVALID"));
    }

    @Test
    public void testVerifyEmailExpiredToken() throws Exception {
        String username = "expired_" + System.currentTimeMillis();
        String email = username + "@example.com";

        User user = User.builder()
                .tenant(defaultTenant)
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode("password123"))
                .emailVerified(false)
                .enabled(true)
                .status(User.UserStatus.ACTIVE)
                .build();
        userRepository.saveAndFlush(user);

        String rawToken = "expiredRawTokenSequence";
        String tokenHash = hashToken(rawToken);

        VerificationToken verificationToken = VerificationToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS)) // Expired
                .used(false)
                .build();
        verificationTokenRepository.saveAndFlush(verificationToken);

        mockMvc.perform(get("/api/v1/auth/verify-email")
                        .param("token", rawToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.errorCode").value("TOKEN_EXPIRED"));
    }

    @Test
    public void testVerifyEmailAlreadyUsedToken() throws Exception {
        String username = "used_" + System.currentTimeMillis();
        String email = username + "@example.com";

        User user = User.builder()
                .tenant(defaultTenant)
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode("password123"))
                .emailVerified(false)
                .enabled(true)
                .status(User.UserStatus.ACTIVE)
                .build();
        userRepository.saveAndFlush(user);

        String rawToken = "usedRawTokenSequence";
        String tokenHash = hashToken(rawToken);

        VerificationToken verificationToken = VerificationToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .used(true) // Already used
                .usedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();
        verificationTokenRepository.saveAndFlush(verificationToken);

        mockMvc.perform(get("/api/v1/auth/verify-email")
                        .param("token", rawToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Verification token has already been used"));
    }

    @Test
    public void testResendVerificationInvalidatesOldTokensAndGeneratesNewOne() throws Exception {
        String username = "resend_" + System.currentTimeMillis();
        String email = username + "@example.com";

        User user = User.builder()
                .tenant(defaultTenant)
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode("password123"))
                .emailVerified(false)
                .enabled(true)
                .status(User.UserStatus.ACTIVE)
                .build();
        userRepository.saveAndFlush(user);

        // Save an old active token
        VerificationToken oldToken = VerificationToken.builder()
                .user(user)
                .tokenHash(hashToken("oldRawToken"))
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .used(false)
                .build();
        verificationTokenRepository.saveAndFlush(oldToken);

        ResendVerificationRequest resendRequest = ResendVerificationRequest.builder()
                .email(email)
                .build();

        mockMvc.perform(post("/api/v1/auth/resend-verification")
                        .header("X-Tenant", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resendRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Verification email has been resent."));

        // Assert old token is now invalidated (used = true)
        VerificationToken updatedOldToken = verificationTokenRepository.findById(oldToken.getId()).orElseThrow();
        assertTrue(updatedOldToken.isUsed());
        assertNotNull(updatedOldToken.getUsedAt());

        // Assert new active token is created
        List<VerificationToken> activeTokens = verificationTokenRepository.findAllByUserAndUsedFalse(user);
        assertEquals(1, activeTokens.size());
        assertNotEquals(oldToken.getTokenHash(), activeTokens.get(0).getTokenHash());

        // Verify email was sent for the new token
        verify(emailService, times(1)).sendVerificationEmail(eq(email), eq(username), anyString());
    }

    private String hashToken(String rawToken) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
