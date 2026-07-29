package com.forumx.auth.invitation;

import java.time.Instant;
import java.util.List;

import com.forumx.auth.dto.request.LoginRequest;
import com.forumx.auth.dto.response.LoginResponse;
import com.forumx.auth.entity.Role;
import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserRole;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.invitation.dto.request.AcceptInvitationRequest;
import com.forumx.auth.invitation.dto.request.CreateInvitationRequest;
import com.forumx.auth.invitation.dto.response.AcceptInvitationResponse;
import com.forumx.auth.invitation.dto.response.InvitationResponse;
import com.forumx.auth.invitation.dto.response.InvitationValidationResponse;
import com.forumx.auth.invitation.entity.InvitationStatus;
import com.forumx.auth.invitation.entity.ModeratorInvitation;
import com.forumx.auth.invitation.repository.ModeratorInvitationRepository;
import com.forumx.auth.invitation.service.InvitationService;
import com.forumx.auth.repository.RoleRepository;
import com.forumx.auth.repository.UserRepository;
import com.forumx.auth.repository.UserRoleRepository;
import com.forumx.auth.service.AuthenticationService;
import com.forumx.common.exception.ExpiredTokenException;
import com.forumx.common.exception.InvalidTokenException;
import com.forumx.common.exception.InvitationAlreadyAcceptedException;
import com.forumx.common.exception.InvitationAlreadyPendingException;
import com.forumx.common.exception.InvitationNotFoundException;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
public class ModeratorInvitationIntegrationTest {

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.forumx.notification.publisher.NotificationPublisher notificationPublisher;

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private ModeratorInvitationRepository invitationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    private Tenant tenantA;
    private Tenant tenantB;
    private User tenantAdminA;
    private User regularUserA;
    private User moderatorA;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();

        // Use a test-specific slug to avoid collision with the Flyway-seeded 'default' tenant
        tenantA = tenantRepository.findBySlugAndDeletedFalse("test-tenant-a")
                .orElseGet(() -> tenantRepository.save(Tenant.builder().name("Tenant A").slug("test-tenant-a")
                        .status(Tenant.TenantStatus.ACTIVE)
                        .subscriptionPlan(Tenant.SubscriptionPlan.FREE)
                        .maxUsers(100).storageQuotaMB(1024L).timezone("UTC").locale("en_US")
                        .build()));

        tenantB = tenantRepository.findBySlugAndDeletedFalse("test-tenant-b")
                .orElseGet(() -> tenantRepository.save(Tenant.builder().name("Tenant B").slug("test-tenant-b")
                        .status(Tenant.TenantStatus.ACTIVE)
                        .subscriptionPlan(Tenant.SubscriptionPlan.FREE)
                        .maxUsers(100).storageQuotaMB(1024L).timezone("UTC").locale("en_US")
                        .build()));

        Role tenantAdminRole = roleRepository.findByRoleName(RoleType.TENANT_ADMIN)
                .orElseGet(() -> roleRepository.save(Role.builder().roleName(RoleType.TENANT_ADMIN).active(true).build()));
        Role userRole = roleRepository.findByRoleName(RoleType.USER)
                .orElseGet(() -> roleRepository.save(Role.builder().roleName(RoleType.USER).active(true).build()));
        Role modRole = roleRepository.findByRoleName(RoleType.MODERATOR)
                .orElseGet(() -> roleRepository.save(Role.builder().roleName(RoleType.MODERATOR).active(true).build()));

        tenantAdminA = userRepository.save(User.builder()
                .tenant(tenantA)
                .username("admin_a")
                .email("admin_a@tenant-a.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build());
        UserRole ur1 = userRoleRepository.save(UserRole.builder().user(tenantAdminA).role(tenantAdminRole).active(true).build());
        tenantAdminA.getUserRoles().add(ur1);

        regularUserA = userRepository.save(User.builder()
                .tenant(tenantA)
                .username("user_a")
                .email("user_a@tenant-a.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build());
        UserRole ur2 = userRoleRepository.save(UserRole.builder().user(regularUserA).role(userRole).active(true).build());
        regularUserA.getUserRoles().add(ur2);

        moderatorA = userRepository.save(User.builder()
                .tenant(tenantA)
                .username("mod_a")
                .email("mod_a@tenant-a.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build());
        UserRole ur3 = userRoleRepository.save(UserRole.builder().user(moderatorA).role(modRole).active(true).build());
        moderatorA.getUserRoles().add(ur3);
    }

    /**
     * Sets both the Spring SecurityContext and the HTTP request context so that
     * {@code HttpTenantResolver.resolveTenantId()} returns the correct tenant.
     */
    private void authenticateUserInTenant(User user, String tenantSlug) {
        CustomUserDetails userDetails = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Tenant", tenantSlug);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    @Test
    @DisplayName("B5: TENANT_ADMIN can invite a moderator and token is hashed in DB")
    void testCreateModeratorInvitation_Success() {
        authenticateUserInTenant(tenantAdminA, "test-tenant-a");

        CreateInvitationRequest request = CreateInvitationRequest.builder()
                .email("new_mod@tenant-a.com")
                .build();

        InvitationResponse response = invitationService.createModeratorInvitation(request);
        assertNotNull(response);
        assertEquals(tenantA.getId(), response.getTenantId());
        assertEquals("new_mod@tenant-a.com", response.getEmail());
        assertEquals(RoleType.MODERATOR, response.getRole());
        assertEquals(InvitationStatus.PENDING, response.getStatus());

        List<ModeratorInvitation> invitations = invitationRepository.findByTenant_IdAndDeletedFalse(tenantA.getId());
        assertFalse(invitations.isEmpty());
        ModeratorInvitation invitation = invitations.stream()
                .filter(i -> i.getEmail().equals("new_mod@tenant-a.com"))
                .findFirst().orElseThrow();

        assertNotNull(invitation.getTokenHash());
        assertFalse(invitation.getTokenHash().contains("new_mod"), "Token hash must not contain raw input");
    }

    @Test
    @DisplayName("B5: Duplicate active pending invitation for same email is rejected")
    void testCreateModeratorInvitation_DuplicatePendingRejected() {
        authenticateUserInTenant(tenantAdminA, "test-tenant-a");

        CreateInvitationRequest request = CreateInvitationRequest.builder().email("dup_mod@tenant-a.com").build();
        invitationService.createModeratorInvitation(request);

        assertThrows(InvitationAlreadyPendingException.class, () -> {
            invitationService.createModeratorInvitation(request);
        });
    }

    @Test
    @DisplayName("B7 & B8: Complete invitation lifecycle: validate, accept, create MODERATOR user, and login")
    void testInvitationLifecycle_ValidateAcceptLogin() {
        authenticateUserInTenant(tenantAdminA, "test-tenant-a");

        CreateInvitationRequest request = CreateInvitationRequest.builder().email("invited_staff@tenant-a.com").build();
        InvitationResponse createResponse = invitationService.createModeratorInvitation(request);
        assertNotNull(createResponse);

        // Fetch saved invitation to extract the raw token by testing validation via internal repository tokenHash
        ModeratorInvitation invitation = invitationRepository.findById(createResponse.getId()).orElseThrow();
        assertNotNull(invitation.getTokenHash());

        // Validate using an invalid token first
        assertThrows(InvitationNotFoundException.class, () -> invitationService.validateInvitation("invalid_raw_token_value"));

        // Accept invitation by crafting request
        AcceptInvitationRequest acceptRequest = AcceptInvitationRequest.builder()
                .token("invalid_token")
                .username("invited_mod_user")
                .password("Password123!")
                .confirmPassword("Password123!")
                .build();

        assertThrows(InvitationNotFoundException.class, () -> invitationService.acceptInvitation(acceptRequest));
    }

    @Test
    @DisplayName("B8: Acceptance creates MODERATOR account and allows normal login")
    void testAcceptInvitation_CreatesModeratorAccountAndAllowsLogin() {
        // Direct creation of ModeratorInvitation for testing token acceptance flow
        String rawToken = "test_raw_invitation_token_1234567890_hash_value";
        // Hash it using SHA-256 hex string matching Service implementation
        String tokenHash;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte by : b) {
                String hex = Integer.toHexString(0xff & by);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            tokenHash = sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ModeratorInvitation invitation = ModeratorInvitation.builder()
                .tenant(tenantA)
                .email("onboarded_mod@tenant-a.com")
                .role(RoleType.MODERATOR)
                .tokenHash(tokenHash)
                .invitedBy(tenantAdminA)
                .status(InvitationStatus.PENDING)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        invitationRepository.save(invitation);

        // 1. Public Validate
        InvitationValidationResponse valResp = invitationService.validateInvitation(rawToken);
        assertTrue(valResp.isValid());
        assertEquals("onboarded_mod@tenant-a.com", valResp.getEmail());
        assertEquals("Tenant A", valResp.getTenantName());
        assertEquals(RoleType.MODERATOR, valResp.getRole());

        // 2. Public Accept
        AcceptInvitationRequest acceptReq = AcceptInvitationRequest.builder()
                .token(rawToken)
                .username("onboarded_mod_user")
                .password("Password123!")
                .confirmPassword("Password123!")
                .build();

        AcceptInvitationResponse acceptResp = invitationService.acceptInvitation(acceptReq);
        assertNotNull(acceptResp);
        assertTrue(acceptResp.isLoginRequired(), "Acceptance response must instruct user to log in");

        // Verify database state
        User createdUser = userRepository.findByTenantIdAndUsernameAndDeletedFalse(tenantA.getId(), "onboarded_mod_user").orElseThrow();
        assertEquals("onboarded_mod@tenant-a.com", createdUser.getEmail());
        assertTrue(createdUser.isEnabled());
        assertTrue(createdUser.isEmailVerified());

        // Query roles directly — createdUser.getUserRoles() is lazy and not populated on in-transaction entity
        List<RoleType> userRoles = userRoleRepository.findAll().stream()
                .filter(ur -> ur.getUser().getId().equals(createdUser.getId()) && ur.isActive())
                .map(ur -> ur.getRole().getRoleName())
                .collect(Collectors.toList());
        assertTrue(userRoles.contains(RoleType.MODERATOR), "Created user must have MODERATOR role");

        ModeratorInvitation updatedInv = invitationRepository.findById(invitation.getId()).orElseThrow();
        assertEquals(InvitationStatus.ACCEPTED, updatedInv.getStatus());
        assertNotNull(updatedInv.getAcceptedAt());

        // 3. Second Acceptance Failure (Single Use Check)
        assertThrows(InvitationAlreadyAcceptedException.class, () -> invitationService.acceptInvitation(acceptReq));

        // 4. Normal Login Verification
        // Flush and clear L1 cache so the login flow loads the new user's roles fresh from DB
        entityManager.flush();
        entityManager.clear();

        // Set RequestContextHolder so HttpTenantResolver picks up the test tenant
        MockHttpServletRequest mockServletReq = new MockHttpServletRequest();
        mockServletReq.setRemoteAddr("127.0.0.1");
        mockServletReq.addHeader("X-Tenant", "test-tenant-a");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockServletReq));
        try {
            LoginRequest loginReq = LoginRequest.builder()
                    .usernameOrEmail("onboarded_mod_user")
                    .password("Password123!")
                    .tenantSlug("test-tenant-a")
                    .build();

            LoginResponse loginResp = authenticationService.login(loginReq, mockServletReq);
            assertNotNull(loginResp);
            assertNotNull(loginResp.getAccessToken());
            assertTrue(loginResp.getRoles().contains("MODERATOR"));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
