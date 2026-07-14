package com.forumx.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forumx.auth.dto.request.ForgotPasswordRequest;
import com.forumx.auth.dto.request.ResetPasswordRequest;
import com.forumx.auth.entity.RefreshToken;
import com.forumx.auth.entity.User;
import com.forumx.auth.repository.RefreshTokenRepository;
import com.forumx.auth.repository.UserRepository;
import com.forumx.auth.passwordreset.entity.PasswordResetToken;
import com.forumx.auth.passwordreset.repository.PasswordResetTokenRepository;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
public class PasswordResetTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

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
                .orElseThrow(() -> new IllegalStateException("Default tenant not found."));
        doNothing().when(emailService).sendPasswordResetEmail(anyString(), anyString(), anyString());
    }

    @Test
    public void testForgotPasswordExistingUserReturnsGenericResponseAndSendsEmail() throws Exception {
        String username = "pwdreset_" + System.currentTimeMillis();
        String email = username + "@example.com";

        User user = User.builder()
                .tenant(defaultTenant)
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode("SecurePassword123!"))
                .emailVerified(true)
                .enabled(true)
                .status(User.UserStatus.ACTIVE)
                .build();
        userRepository.saveAndFlush(user);

        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email(email)
                .build();

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .header("X-Tenant", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("If an account exists for that email, a password reset link has been sent."));

        // Verify Reset Token generated in DB
        List<PasswordResetToken> tokens = passwordResetTokenRepository.findAllByUserAndUsedFalse(user);
        assertEquals(1, tokens.size());
        assertNotNull(tokens.get(0).getTokenHash());

        // Verify Email Sent
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendPasswordResetEmail(eq(email), eq(username), urlCaptor.capture());
        assertTrue(urlCaptor.getValue().startsWith("http://localhost:3000/reset-password?token="));
    }

    @Test
    public void testForgotPasswordNonExistingUserReturnsGenericResponseAndDoesNotSendEmail() throws Exception {
        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email("nonexistent_email_address_12345@example.com")
                .build();

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .header("X-Tenant", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("If an account exists for that email, a password reset link has been sent."));

        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString());
    }

    @Test
    public void testResetPasswordSuccessUpdatesPasswordAndRevokesRefreshTokens() throws Exception {
        String username = "pwdreset_success_" + System.currentTimeMillis();
        String email = username + "@example.com";
        String oldPassword = "OldSecurePassword123!";
        String newPassword = "NewSecurePassword123!";

        User user = User.builder()
                .tenant(defaultTenant)
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(oldPassword))
                .emailVerified(true)
                .enabled(true)
                .status(User.UserStatus.ACTIVE)
                .build();
        userRepository.saveAndFlush(user);

        // Active refresh token
        RefreshToken activeToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(false)
                .build();
        refreshTokenRepository.saveAndFlush(activeToken);

        String rawToken = "rawResetPasswordTokenValueForSuccessCase";
        String tokenHash = hashToken(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
                .used(false)
                .build();
        passwordResetTokenRepository.saveAndFlush(resetToken);

        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token(rawToken)
                .newPassword(newPassword)
                .confirmPassword(newPassword)
                .build();

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Password reset successfully"));

        // Verify DB password updated
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches(newPassword, updatedUser.getPasswordHash()));
        assertFalse(passwordEncoder.matches(oldPassword, updatedUser.getPasswordHash()));

        // Verify Reset Token marked used
        PasswordResetToken updatedResetToken = passwordResetTokenRepository.findById(resetToken.getId()).orElseThrow();
        assertTrue(updatedResetToken.isUsed());
        assertNotNull(updatedResetToken.getUsedAt());

        // Verify Refresh Token revoked
        RefreshToken updatedRefreshToken = refreshTokenRepository.findById(activeToken.getId()).orElseThrow();
        assertTrue(updatedRefreshToken.isRevoked());
        assertNotNull(updatedRefreshToken.getRevokedAt());
    }

    @Test
    public void testResetPasswordInvalidTokenThrowsInvalidToken() throws Exception {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("nonexistent_reset_token")
                .newPassword("NewSecurePassword123!")
                .confirmPassword("NewSecurePassword123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.errorCode").value("TOKEN_INVALID"));
    }

    @Test
    public void testResetPasswordExpiredTokenThrowsExpiredToken() throws Exception {
        String username = "pwdreset_expired_" + System.currentTimeMillis();
        String email = username + "@example.com";

        User user = User.builder()
                .tenant(defaultTenant)
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode("password123"))
                .emailVerified(true)
                .enabled(true)
                .status(User.UserStatus.ACTIVE)
                .build();
        userRepository.saveAndFlush(user);

        String rawToken = "expiredResetTokenSequence";
        String tokenHash = hashToken(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .used(false)
                .build();
        passwordResetTokenRepository.saveAndFlush(resetToken);

        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token(rawToken)
                .newPassword("NewSecurePassword123!")
                .confirmPassword("NewSecurePassword123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.errorCode").value("TOKEN_EXPIRED"));
    }

    @Test
    public void testResetPasswordAlreadyUsedTokenThrowsAlreadyUsed() throws Exception {
        String username = "pwdreset_used_" + System.currentTimeMillis();
        String email = username + "@example.com";

        User user = User.builder()
                .tenant(defaultTenant)
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode("password123"))
                .emailVerified(true)
                .enabled(true)
                .status(User.UserStatus.ACTIVE)
                .build();
        userRepository.saveAndFlush(user);

        String rawToken = "usedResetTokenSequence";
        String tokenHash = hashToken(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
                .used(true)
                .usedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();
        passwordResetTokenRepository.saveAndFlush(resetToken);

        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token(rawToken)
                .newPassword("NewSecurePassword123!")
                .confirmPassword("NewSecurePassword123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Password reset token has already been used"));
    }

    @Test
    public void testResetPasswordMismatchThrowsPasswordMismatch() throws Exception {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("any_token")
                .newPassword("PasswordOne123!")
                .confirmPassword("PasswordTwo123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.errorCode").value("PASSWORDS_DO_NOT_MATCH"))
                .andExpect(jsonPath("$.message").value("Passwords do not match"));
    }

    @Test
    public void testResetPasswordReuseThrowsPasswordReuse() throws Exception {
        String username = "pwdreset_reuse_" + System.currentTimeMillis();
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

        String rawToken = "reuseResetTokenSequence";
        String tokenHash = hashToken(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
                .used(false)
                .build();
        passwordResetTokenRepository.saveAndFlush(resetToken);

        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token(rawToken)
                .newPassword(password) // Reusing password
                .confirmPassword(password)
                .build();

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.errorCode").value("PASSWORD_REUSE"))
                .andExpect(jsonPath("$.message").value("Cannot reset password to the current password"));
    }

    @Test
    public void testForgotPasswordInvalidatesPriorResetTokens() throws Exception {
        String username = "pwdreset_invalidate_" + System.currentTimeMillis();
        String email = username + "@example.com";

        User user = User.builder()
                .tenant(defaultTenant)
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode("SecurePassword123!"))
                .emailVerified(true)
                .enabled(true)
                .status(User.UserStatus.ACTIVE)
                .build();
        userRepository.saveAndFlush(user);

        PasswordResetToken priorToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash(hashToken("priorRawToken"))
                .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
                .used(false)
                .build();
        passwordResetTokenRepository.saveAndFlush(priorToken);

        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email(email)
                .build();

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .header("X-Tenant", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Assert old token marked used
        PasswordResetToken updatedPriorToken = passwordResetTokenRepository.findById(priorToken.getId()).orElseThrow();
        assertTrue(updatedPriorToken.isUsed());
        assertNotNull(updatedPriorToken.getUsedAt());

        // Assert only one active token remains
        List<PasswordResetToken> activeTokens = passwordResetTokenRepository.findAllByUserAndUsedFalse(user);
        assertEquals(1, activeTokens.size());
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
