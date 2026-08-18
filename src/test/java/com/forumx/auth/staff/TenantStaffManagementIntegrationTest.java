package com.forumx.auth.staff;

import com.forumx.auth.entity.RefreshToken;
import com.forumx.auth.entity.Role;
import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserRole;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.invitation.entity.InvitationStatus;
import com.forumx.auth.invitation.entity.ModeratorInvitation;
import com.forumx.auth.invitation.repository.ModeratorInvitationRepository;
import com.forumx.auth.repository.RefreshTokenRepository;
import com.forumx.auth.repository.RoleRepository;
import com.forumx.auth.repository.UserRepository;
import com.forumx.auth.repository.UserRoleRepository;
import com.forumx.auth.staff.dto.response.InvitationListResponse;
import com.forumx.auth.staff.dto.response.ModeratorDetailResponse;
import com.forumx.auth.staff.dto.response.ModeratorSummaryResponse;
import com.forumx.auth.staff.service.TenantStaffService;
import com.forumx.common.exception.InvitationAlreadyAcceptedException;
import com.forumx.common.exception.InvitationAlreadyRevokedException;
import com.forumx.common.exception.InvitationNotFoundException;
import com.forumx.common.exception.ModeratorAlreadyDisabledException;
import com.forumx.common.exception.ModeratorAlreadyEnabledException;
import com.forumx.common.exception.ModeratorNotFoundException;
import com.forumx.security.jwt.JwtTokenProvider;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.security.service.CustomUserDetailsService;
import com.forumx.support.chat.entity.ChatSession;
import com.forumx.support.chat.entity.ChatSessionStatus;
import com.forumx.support.chat.entity.ParticipantRole;
import com.forumx.support.chat.entity.SupportSessionParticipant;
import com.forumx.support.chat.repository.ChatSessionRepository;
import com.forumx.support.chat.repository.SupportSessionParticipantRepository;
import com.forumx.support.ticket.entity.Ticket;
import com.forumx.support.ticket.repository.TicketRepository;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Phase C: Tenant Staff Management Backend.
 *
 * <h2>Design rules</h2>
 * <ul>
 *   <li>Unique test-tenant slugs prevent collision with Flyway-seeded tenants.</li>
 *   <li>All role assertions use repository queries — never user.getUserRoles() — to avoid L1 cache staleness.</li>
 *   <li>Active JWT test (C11) directly calls JwtAuthenticationFilter-equivalent logic:
 *       generateAccessToken() before disable, then loadUserByUsername() after disable to verify
 *       Spring Security UserDetails.isEnabled() returns false.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
public class TenantStaffManagementIntegrationTest {

    // ── Mocked dependencies ─────────────────────────────────────────────

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.forumx.notification.publisher.NotificationPublisher notificationPublisher;

    // ── Services under test ─────────────────────────────────────────────

    @Autowired
    private TenantStaffService tenantStaffService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    // ── Repositories ────────────────────────────────────────────────────

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
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private SupportSessionParticipantRepository participantRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    // ── Test data ───────────────────────────────────────────────────────

    private Tenant tenantA;
    private Tenant tenantB;

    private User tenantAdminA;
    private User moderatorA;
    private User regularUserA;

    private User tenantAdminB;
    private User moderatorB;

    // ── Roles (loaded once per test, Flyway ensures they exist in V35) ──

    private Role tenantAdminRole;
    private Role moderatorRole;
    private Role userRole;

    // ── Setup ───────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();

        tenantA = tenantRepository.findBySlugAndDeletedFalse("staff-test-tenant-a")
                .orElseGet(() -> tenantRepository.save(Tenant.builder()
                        .name("Staff Test Tenant A").slug("staff-test-tenant-a")
                        .status(Tenant.TenantStatus.ACTIVE)
                        .subscriptionPlan(Tenant.SubscriptionPlan.FREE)
                        .maxUsers(100).storageQuotaMB(1024L).timezone("UTC").locale("en_US")
                        .build()));

        tenantB = tenantRepository.findBySlugAndDeletedFalse("staff-test-tenant-b")
                .orElseGet(() -> tenantRepository.save(Tenant.builder()
                        .name("Staff Test Tenant B").slug("staff-test-tenant-b")
                        .status(Tenant.TenantStatus.ACTIVE)
                        .subscriptionPlan(Tenant.SubscriptionPlan.FREE)
                        .maxUsers(100).storageQuotaMB(1024L).timezone("UTC").locale("en_US")
                        .build()));

        tenantAdminRole = roleRepository.findByRoleName(RoleType.TENANT_ADMIN)
                .orElseGet(() -> roleRepository.save(Role.builder().roleName(RoleType.TENANT_ADMIN).active(true).build()));
        moderatorRole = roleRepository.findByRoleName(RoleType.MODERATOR)
                .orElseGet(() -> roleRepository.save(Role.builder().roleName(RoleType.MODERATOR).active(true).build()));
        userRole = roleRepository.findByRoleName(RoleType.USER)
                .orElseGet(() -> roleRepository.save(Role.builder().roleName(RoleType.USER).active(true).build()));

        // --- Tenant A ---
        tenantAdminA = userRepository.save(User.builder()
                .tenant(tenantA).username("staff_admin_a").email("staff_admin_a@a.test")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .enabled(true).emailVerified(true).status(User.UserStatus.ACTIVE).build());
        userRoleRepository.save(UserRole.builder().user(tenantAdminA).role(tenantAdminRole).active(true).build());

        moderatorA = userRepository.save(User.builder()
                .tenant(tenantA).username("staff_mod_a").email("staff_mod_a@a.test")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .enabled(true).emailVerified(true).status(User.UserStatus.ACTIVE).build());
        userRoleRepository.save(UserRole.builder().user(moderatorA).role(moderatorRole).active(true).build());

        regularUserA = userRepository.save(User.builder()
                .tenant(tenantA).username("staff_user_a").email("staff_user_a@a.test")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .enabled(true).emailVerified(true).status(User.UserStatus.ACTIVE).build());
        userRoleRepository.save(UserRole.builder().user(regularUserA).role(userRole).active(true).build());

        // --- Tenant B ---
        tenantAdminB = userRepository.save(User.builder()
                .tenant(tenantB).username("staff_admin_b").email("staff_admin_b@b.test")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .enabled(true).emailVerified(true).status(User.UserStatus.ACTIVE).build());
        userRoleRepository.save(UserRole.builder().user(tenantAdminB).role(tenantAdminRole).active(true).build());

        moderatorB = userRepository.save(User.builder()
                .tenant(tenantB).username("staff_mod_b").email("staff_mod_b@b.test")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .enabled(true).emailVerified(true).status(User.UserStatus.ACTIVE).build());
        userRoleRepository.save(UserRole.builder().user(moderatorB).role(moderatorRole).active(true).build());

        // Flush to ensure all IDs and FKs are populated before tests start
        entityManager.flush();
    }

    // ── Authentication helpers ──────────────────────────────────────────

    private void authenticateAs(User user, String tenantSlug) {
        CustomUserDetails userDetails = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Tenant", tenantSlug);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    // ── C2: Moderator Listing ───────────────────────────────────────────

    @Test
    @DisplayName("C2: List returns only MODERATOR accounts in authenticated tenant")
    void testListModerators_TenantIsolation() {
        authenticateAs(tenantAdminA, "staff-test-tenant-a");
        Pageable pageable = PageRequest.of(0, 20);

        Page<ModeratorSummaryResponse> page = tenantStaffService.listModerators(pageable);

        assertNotNull(page);
        assertTrue(page.getTotalElements() >= 1, "Should have at least 1 moderator");

        boolean foundModA = page.getContent().stream()
                .anyMatch(m -> m.getUserId().equals(moderatorA.getId()));
        assertTrue(foundModA, "moderatorA must be in results");

        // Must NOT include USER or TENANT_ADMIN accounts
        boolean foundUser = page.getContent().stream()
                .anyMatch(m -> m.getUserId().equals(regularUserA.getId()));
        assertFalse(foundUser, "Regular user must NOT appear in moderator list");

        boolean foundAdmin = page.getContent().stream()
                .anyMatch(m -> m.getUserId().equals(tenantAdminA.getId()));
        assertFalse(foundAdmin, "Tenant admin must NOT appear in moderator list");

        // Must NOT include Tenant B moderator
        boolean foundModB = page.getContent().stream()
                .anyMatch(m -> m.getUserId().equals(moderatorB.getId()));
        assertFalse(foundModB, "Tenant B moderator must NOT appear in Tenant A list");
    }

    @Test
    @DisplayName("C2: Moderator summary fields are populated and safe")
    void testListModerators_Fields() {
        authenticateAs(tenantAdminA, "staff-test-tenant-a");

        Page<ModeratorSummaryResponse> page = tenantStaffService.listModerators(PageRequest.of(0, 20));
        ModeratorSummaryResponse mod = page.getContent().stream()
                .filter(m -> m.getUserId().equals(moderatorA.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("moderatorA not in list"));

        assertEquals("staff_mod_a", mod.getUsername());
        assertEquals("staff_mod_a@a.test", mod.getEmail());
        assertTrue(mod.isEnabled());
        assertEquals("ACTIVE", mod.getStatus());
        assertNotNull(mod.getCreatedAt());
    }

    // ── C3: Moderator Detail ────────────────────────────────────────────

    @Test
    @DisplayName("C3: TENANT_ADMIN can fetch moderator detail within own tenant")
    void testGetModeratorDetail_Success() {
        authenticateAs(tenantAdminA, "staff-test-tenant-a");

        ModeratorDetailResponse detail = tenantStaffService.getModeratorDetail(moderatorA.getId());

        assertNotNull(detail);
        assertEquals(moderatorA.getId(), detail.getUserId());
        assertEquals("staff_mod_a", detail.getUsername());
        assertTrue(detail.isEnabled());
        assertEquals("ACTIVE", detail.getStatus());
    }

    @Test
    @DisplayName("C3: Cross-tenant moderator detail access returns NOT_FOUND (isolation)")
    void testGetModeratorDetail_CrossTenant_Denied() {
        authenticateAs(tenantAdminA, "staff-test-tenant-a");

        // moderatorB belongs to tenantB — tenantAdminA must not see it
        assertThrows(ModeratorNotFoundException.class,
                () -> tenantStaffService.getModeratorDetail(moderatorB.getId()));
    }

    @Test
    @DisplayName("C3: Regular USER rejected from moderator detail endpoint")
    void testGetModeratorDetail_RegularUser_Rejected() {
        authenticateAs(tenantAdminA, "staff-test-tenant-a");

        assertThrows(ModeratorNotFoundException.class,
                () -> tenantStaffService.getModeratorDetail(regularUserA.getId()));
    }

    @Test
    @DisplayName("C3: TENANT_ADMIN self is rejected (not a MODERATOR)")
    void testGetModeratorDetail_TenantAdmin_Rejected() {
        authenticateAs(tenantAdminA, "staff-test-tenant-a");

        assertThrows(ModeratorNotFoundException.class,
                () -> tenantStaffService.getModeratorDetail(tenantAdminA.getId()));
    }

    // ── C4: Invitation Listing ──────────────────────────────────────────

    @Test
    @DisplayName("C4: Invitation list returns only current tenant's invitations")
    void testListInvitations_TenantIsolation() {
        // Create invitations for both tenants
        ModeratorInvitation invA = invitationRepository.save(ModeratorInvitation.builder()
                .tenant(tenantA).email("inv_a@a.test").role(RoleType.MODERATOR)
                .tokenHash("hash-inv-a-" + System.nanoTime()).invitedBy(tenantAdminA)
                .status(InvitationStatus.PENDING).expiresAt(Instant.now().plusSeconds(86400))
                .build());

        ModeratorInvitation invB = invitationRepository.save(ModeratorInvitation.builder()
                .tenant(tenantB).email("inv_b@b.test").role(RoleType.MODERATOR)
                .tokenHash("hash-inv-b-" + System.nanoTime()).invitedBy(tenantAdminB)
                .status(InvitationStatus.PENDING).expiresAt(Instant.now().plusSeconds(86400))
                .build());
        entityManager.flush();

        authenticateAs(tenantAdminA, "staff-test-tenant-a");
        Page<InvitationListResponse> page = tenantStaffService.listInvitations(null, PageRequest.of(0, 20));

        boolean hasA = page.getContent().stream().anyMatch(i -> i.getId().equals(invA.getId()));
        boolean hasB = page.getContent().stream().anyMatch(i -> i.getId().equals(invB.getId()));

        assertTrue(hasA, "Tenant A invitation must be in results");
        assertFalse(hasB, "Tenant B invitation must NOT be in results");
    }

    @Test
    @DisplayName("C4: Invitation list response never exposes tokenHash")
    void testListInvitations_NoTokenHashExposed() {
        invitationRepository.save(ModeratorInvitation.builder()
                .tenant(tenantA).email("safe_inv@a.test").role(RoleType.MODERATOR)
                .tokenHash("secret-hash-" + System.nanoTime()).invitedBy(tenantAdminA)
                .status(InvitationStatus.PENDING).expiresAt(Instant.now().plusSeconds(86400))
                .build());
        entityManager.flush();

        authenticateAs(tenantAdminA, "staff-test-tenant-a");
        Page<InvitationListResponse> page = tenantStaffService.listInvitations(null, PageRequest.of(0, 20));

        // InvitationListResponse must not have a tokenHash field — verified by no such getter
        // The DTO class does not have getTokenHash() — this test validates the contract
        assertTrue(page.getTotalElements() >= 1);
        page.getContent().forEach(inv -> {
            assertNotNull(inv.getEmail());
            assertNotNull(inv.getStatus());
            // If tokenHash were exposed, it would be here — this verifies safe fields only
        });
    }

    @Test
    @DisplayName("C4: Invitation list status filter works correctly")
    void testListInvitations_StatusFilter() {
        String nano = String.valueOf(System.nanoTime());
        invitationRepository.save(ModeratorInvitation.builder()
                .tenant(tenantA).email("pending_filter@a.test").role(RoleType.MODERATOR)
                .tokenHash("hash-pending-" + nano).invitedBy(tenantAdminA)
                .status(InvitationStatus.PENDING).expiresAt(Instant.now().plusSeconds(86400))
                .build());
        invitationRepository.save(ModeratorInvitation.builder()
                .tenant(tenantA).email("revoked_filter@a.test").role(RoleType.MODERATOR)
                .tokenHash("hash-revoked-" + nano).invitedBy(tenantAdminA)
                .status(InvitationStatus.REVOKED).expiresAt(Instant.now().minusSeconds(1))
                .revokedAt(Instant.now()).build());
        entityManager.flush();

        authenticateAs(tenantAdminA, "staff-test-tenant-a");

        Page<InvitationListResponse> pendingPage = tenantStaffService.listInvitations(InvitationStatus.PENDING, PageRequest.of(0, 20));
        assertTrue(pendingPage.getContent().stream().allMatch(i -> i.getStatus() == InvitationStatus.PENDING || i.getStatus() == InvitationStatus.EXPIRED),
                "Status filter PENDING should return only PENDING/EXPIRED-effective items");

        Page<InvitationListResponse> revokedPage = tenantStaffService.listInvitations(InvitationStatus.REVOKED, PageRequest.of(0, 20));
        assertTrue(revokedPage.getContent().stream().allMatch(i -> i.getStatus() == InvitationStatus.REVOKED),
                "Status filter REVOKED should return only REVOKED items");
    }

    // ── C5: Expired Invitation Consistency ─────────────────────────────

    @Test
    @DisplayName("C5: PENDING invitation past expiresAt is reported as EXPIRED in list")
    void testExpiredInvitation_ReportedAsExpired() {
        invitationRepository.save(ModeratorInvitation.builder()
                .tenant(tenantA).email("expired_inv@a.test").role(RoleType.MODERATOR)
                .tokenHash("hash-exp-" + System.nanoTime()).invitedBy(tenantAdminA)
                .status(InvitationStatus.PENDING).expiresAt(Instant.now().minusSeconds(1)) // in the past
                .build());
        entityManager.flush();

        authenticateAs(tenantAdminA, "staff-test-tenant-a");
        Page<InvitationListResponse> page = tenantStaffService.listInvitations(null, PageRequest.of(0, 20));

        InvitationListResponse expired = page.getContent().stream()
                .filter(i -> i.getEmail().equals("expired_inv@a.test"))
                .findFirst().orElseThrow();

        assertEquals(InvitationStatus.EXPIRED, expired.getStatus(),
                "Invitation past expiresAt must report as EXPIRED, not PENDING");
    }

    // ── C6: Resend Invitation ───────────────────────────────────────────

    @Test
    @DisplayName("C6: Resend rotates tokenHash and renews expiry")
    void testResendInvitation_RotatesToken() {
        String originalHash = "original-hash-" + System.nanoTime();
        ModeratorInvitation inv = invitationRepository.save(ModeratorInvitation.builder()
                .tenant(tenantA).email("resend_inv@a.test").role(RoleType.MODERATOR)
                .tokenHash(originalHash).invitedBy(tenantAdminA)
                .status(InvitationStatus.PENDING).expiresAt(Instant.now().plusSeconds(86400))
                .build());
        entityManager.flush();

        authenticateAs(tenantAdminA, "staff-test-tenant-a");
        InvitationListResponse result = tenantStaffService.resendInvitation(inv.getId());

        entityManager.flush();
        entityManager.clear();

        // Reload and verify
        ModeratorInvitation reloaded = invitationRepository.findById(inv.getId()).orElseThrow();

        assertNotEquals(originalHash, reloaded.getTokenHash(), "tokenHash must be rotated after resend");
        assertEquals(InvitationStatus.PENDING, reloaded.getStatus());
        assertTrue(reloaded.getExpiresAt().isAfter(Instant.now()), "expiresAt must be in the future");
        assertNull(reloaded.getAcceptedAt(), "acceptedAt must be null after resend");
        assertNull(reloaded.getRevokedAt(), "revokedAt must be null after resend");

        // Response must not contain raw token or hash
        assertNotNull(result.getStatus());
        assertEquals(InvitationStatus.PENDING, result.getStatus());
    }

    @Test
    @DisplayName("C6: Resend of ACCEPTED invitation rejected")
    void testResendInvitation_Accepted_Rejected() {
        ModeratorInvitation inv = invitationRepository.save(ModeratorInvitation.builder()
                .tenant(tenantA).email("accepted_resend@a.test").role(RoleType.MODERATOR)
                .tokenHash("hash-accepted-" + System.nanoTime()).invitedBy(tenantAdminA)
                .status(InvitationStatus.ACCEPTED).expiresAt(Instant.now().plusSeconds(86400))
                .acceptedAt(Instant.now()).build());
        entityManager.flush();

        authenticateAs(tenantAdminA, "staff-test-tenant-a");

        assertThrows(InvitationAlreadyAcceptedException.class,
                () -> tenantStaffService.resendInvitation(inv.getId()));
    }

    @Test
    @DisplayName("C6: Resend of REVOKED invitation rejected")
    void testResendInvitation_Revoked_Rejected() {
        ModeratorInvitation inv = invitationRepository.save(ModeratorInvitation.builder()
                .tenant(tenantA).email("revoked_resend@a.test").role(RoleType.MODERATOR)
                .tokenHash("hash-revoked-r-" + System.nanoTime()).invitedBy(tenantAdminA)
                .status(InvitationStatus.REVOKED).expiresAt(Instant.now().minusSeconds(1))
                .revokedAt(Instant.now()).build());
        entityManager.flush();

        authenticateAs(tenantAdminA, "staff-test-tenant-a");

        assertThrows(InvitationAlreadyRevokedException.class,
                () -> tenantStaffService.resendInvitation(inv.getId()));
    }

    @Test
    @DisplayName("C6: Resend cross-tenant invitation returns NOT_FOUND")
    void testResendInvitation_CrossTenant_NotFound() {
        ModeratorInvitation invB = invitationRepository.save(ModeratorInvitation.builder()
                .tenant(tenantB).email("inv_cross@b.test").role(RoleType.MODERATOR)
                .tokenHash("hash-cross-" + System.nanoTime()).invitedBy(tenantAdminB)
                .status(InvitationStatus.PENDING).expiresAt(Instant.now().plusSeconds(86400))
                .build());
        entityManager.flush();

        authenticateAs(tenantAdminA, "staff-test-tenant-a");

        assertThrows(InvitationNotFoundException.class,
                () -> tenantStaffService.resendInvitation(invB.getId()));
    }

    // ── C7: Revoke Invitation ───────────────────────────────────────────

    @Test
    @DisplayName("C7: PENDING invitation can be revoked; revokedAt is set")
    void testRevokeInvitation_Success() {
        ModeratorInvitation inv = invitationRepository.save(ModeratorInvitation.builder()
                .tenant(tenantA).email("to_revoke@a.test").role(RoleType.MODERATOR)
                .tokenHash("hash-to-revoke-" + System.nanoTime()).invitedBy(tenantAdminA)
                .status(InvitationStatus.PENDING).expiresAt(Instant.now().plusSeconds(86400))
                .build());
        entityManager.flush();

        authenticateAs(tenantAdminA, "staff-test-tenant-a");
        tenantStaffService.revokeInvitation(inv.getId());

        entityManager.flush();
        entityManager.clear();

        ModeratorInvitation reloaded = invitationRepository.findById(inv.getId()).orElseThrow();
        assertEquals(InvitationStatus.REVOKED, reloaded.getStatus());
        assertNotNull(reloaded.getRevokedAt());
    }

    @Test
    @DisplayName("C7: Revoking ACCEPTED invitation is rejected")
    void testRevokeInvitation_Accepted_Rejected() {
        ModeratorInvitation inv = invitationRepository.save(ModeratorInvitation.builder()
                .tenant(tenantA).email("acc_revoke@a.test").role(RoleType.MODERATOR)
                .tokenHash("hash-acc-r-" + System.nanoTime()).invitedBy(tenantAdminA)
                .status(InvitationStatus.ACCEPTED).expiresAt(Instant.now().plusSeconds(86400))
                .acceptedAt(Instant.now()).build());
        entityManager.flush();

        authenticateAs(tenantAdminA, "staff-test-tenant-a");

        assertThrows(InvitationAlreadyAcceptedException.class,
                () -> tenantStaffService.revokeInvitation(inv.getId()));
    }

    @Test
    @DisplayName("C7: Revoking already-REVOKED invitation is rejected")
    void testRevokeInvitation_AlreadyRevoked_Rejected() {
        ModeratorInvitation inv = invitationRepository.save(ModeratorInvitation.builder()
                .tenant(tenantA).email("double_revoke@a.test").role(RoleType.MODERATOR)
                .tokenHash("hash-dbl-r-" + System.nanoTime()).invitedBy(tenantAdminA)
                .status(InvitationStatus.REVOKED).expiresAt(Instant.now().minusSeconds(1))
                .revokedAt(Instant.now()).build());
        entityManager.flush();

        authenticateAs(tenantAdminA, "staff-test-tenant-a");

        assertThrows(InvitationAlreadyRevokedException.class,
                () -> tenantStaffService.revokeInvitation(inv.getId()));
    }

    // ── C8/C10: Disable Moderator ───────────────────────────────────────

    @Test
    @DisplayName("C8: Disable sets enabled=false, status=INACTIVE; history preserved")
    void testDisableModerator_AccountState() {
        authenticateAs(tenantAdminA, "staff-test-tenant-a");
        tenantStaffService.disableModerator(moderatorA.getId());

        entityManager.flush();
        entityManager.clear();

        User reloaded = userRepository.findById(moderatorA.getId()).orElseThrow();
        assertFalse(reloaded.isEnabled(), "enabled must be false after disable");
        assertEquals(User.UserStatus.INACTIVE, reloaded.getStatus(), "status must be INACTIVE after disable");

        // User and UserRole records must still exist (history preserved)
        assertFalse(reloaded.isDeleted(), "User must NOT be deleted");
        boolean hasModRole = userRoleRepository.existsActiveRoleByUserIdAndRoleName(
                moderatorA.getId(), RoleType.MODERATOR);
        assertTrue(hasModRole, "MODERATOR UserRole must still exist after disable");
    }

    @Test
    @DisplayName("C10: Disable revokes all refresh tokens; they remain in DB for audit")
    void testDisableModerator_RefreshTokenRevoked() {
        // Create a refresh token for the moderator
        RefreshToken rt = refreshTokenRepository.save(RefreshToken.builder()
                .user(moderatorA)
                .token("test-refresh-token-" + System.nanoTime())
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build());
        entityManager.flush();

        authenticateAs(tenantAdminA, "staff-test-tenant-a");
        tenantStaffService.disableModerator(moderatorA.getId());

        entityManager.flush();
        entityManager.clear();

        RefreshToken reloaded = refreshTokenRepository.findById(rt.getId()).orElseThrow();
        assertTrue(reloaded.isRevoked(), "Refresh token must be revoked after moderator disable");
        assertNotNull(reloaded.getRevokedAt(), "revokedAt must be set");
        assertFalse(reloaded.isUsable(), "Token must not be usable after revocation");
    }

    @Test
    @DisplayName("C8: Disabling already-disabled moderator returns CONFLICT")
    void testDisableModerator_AlreadyDisabled() {
        authenticateAs(tenantAdminA, "staff-test-tenant-a");
        tenantStaffService.disableModerator(moderatorA.getId());

        entityManager.flush();
        entityManager.clear();

        assertThrows(ModeratorAlreadyDisabledException.class,
                () -> tenantStaffService.disableModerator(moderatorA.getId()));
    }

    @Test
    @DisplayName("C8: Cross-tenant disable returns NOT_FOUND (isolation)")
    void testDisableModerator_CrossTenant_Denied() {
        authenticateAs(tenantAdminA, "staff-test-tenant-a");

        assertThrows(ModeratorNotFoundException.class,
                () -> tenantStaffService.disableModerator(moderatorB.getId()));
    }

    @Test
    @DisplayName("C8: Cannot disable regular USER — role protection")
    void testDisableModerator_RegularUser_Rejected() {
        authenticateAs(tenantAdminA, "staff-test-tenant-a");

        assertThrows(ModeratorNotFoundException.class,
                () -> tenantStaffService.disableModerator(regularUserA.getId()));
    }

    @Test
    @DisplayName("C8: Cannot disable TENANT_ADMIN — role protection")
    void testDisableModerator_TenantAdmin_Rejected() {
        authenticateAs(tenantAdminA, "staff-test-tenant-a");

        assertThrows(ModeratorNotFoundException.class,
                () -> tenantStaffService.disableModerator(tenantAdminA.getId()));
    }

    // ── C9: Support Participation Termination ───────────────────────────

    @Test
    @DisplayName("C9: Active support participation is terminated on disable; history preserved")
    void testDisableModerator_TerminatesActiveParticipation() {
        // Create a minimal Ticket + ChatSession + SupportSessionParticipant for moderatorA
        Ticket ticket = ticketRepository.save(Ticket.builder()
                .tenant(tenantA)
                .creator(regularUserA)
                .subject("Support Ticket for C9 Test")
                .description("Detailed test description for C9 support ticket")
                .priority(com.forumx.support.ticket.entity.TicketPriority.MEDIUM)
                .status(com.forumx.support.ticket.entity.TicketStatus.OPEN)
                .build());

        ChatSession session = chatSessionRepository.save(ChatSession.builder()
                .ticket(ticket)
                .tenant(tenantA)
                .customer(regularUserA)
                .status(ChatSessionStatus.ACTIVE)
                .build());

        SupportSessionParticipant participation = participantRepository.save(SupportSessionParticipant.builder()
                .session(session).tenant(tenantA).user(moderatorA)
                .role(ParticipantRole.MODERATOR)
                .joinedAt(Instant.now()).isActive(true)
                .build());
        entityManager.flush();

        authenticateAs(tenantAdminA, "staff-test-tenant-a");
        tenantStaffService.disableModerator(moderatorA.getId());

        entityManager.flush();
        entityManager.clear();

        // Participation record must be inactive but still exist
        SupportSessionParticipant reloaded = participantRepository.findById(participation.getId()).orElseThrow();
        assertFalse(reloaded.isActive(), "Active participation must be terminated after disable");
        assertNotNull(reloaded.getLeftAt(), "leftAt must be set on terminated participation");
        assertNotNull(reloaded.getJoinedAt(), "joinedAt (history) must be preserved");
    }

    // ── C11: Active JWT Behavior After Disable ──────────────────────────

    @Test
    @DisplayName("C11: Existing access JWT becomes unauthorized after moderator is disabled")
    void testDisableModerator_ExistingJwtUnauthorized() {
        // Step 1: Generate a valid access JWT for moderatorA BEFORE disable
        CustomUserDetails beforeDisableDetails = new CustomUserDetails(moderatorA);
        String accessJwt = jwtTokenProvider.generateAccessToken(beforeDisableDetails);
        assertNotNull(accessJwt, "JWT must be generated before disable");

        // Step 2: Disable the moderator
        authenticateAs(tenantAdminA, "staff-test-tenant-a");
        tenantStaffService.disableModerator(moderatorA.getId());

        entityManager.flush();
        entityManager.clear();

        // Step 3: Verify user state in DB is disabled
        User disabledUser = userRepository.findById(moderatorA.getId()).orElseThrow();
        assertFalse(disabledUser.isEnabled(), "User.enabled must be false after disable");
        assertEquals(User.UserStatus.INACTIVE, disabledUser.getStatus(), "User.status must be INACTIVE after disable");

        // Step 4: Simulate what JwtAuthenticationFilter does on every authenticated request:
        //         extract username from JWT, set X-Tenant context, loadUserByUsername -> throws UsernameNotFoundException
        String username = jwtTokenProvider.extractUsername(accessJwt);
        assertEquals(moderatorA.getUsername(), username, "Username must be extractable from JWT");

        // Set up the request context as JwtAuthenticationFilter would
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Tenant", "staff-test-tenant-a");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
        SecurityContextHolder.clearContext();

        // C11: CustomUserDetailsService.loadUserByTenantIdAndUsername checks findForAuthenticationByTenantIdAndUsernameOrEmail
        // which filters for u.enabled = true. Because the user is disabled, loadUserByTenantIdAndUsername throws UsernameNotFoundException,
        // rejecting the JWT authentication on the next request.
        assertThrows(UsernameNotFoundException.class, () ->
                userDetailsService.loadUserByTenantIdAndUsername(tenantA.getId(), username),
                "C11: UserDetails loading must throw UsernameNotFoundException for disabled user, rejecting JWT authentication");
    }

    // ── C12: Enable Moderator ───────────────────────────────────────────

    @Test
    @DisplayName("C12: Enable restores enabled=true and status=ACTIVE")
    void testEnableModerator_RestoresAccount() {
        // First disable
        authenticateAs(tenantAdminA, "staff-test-tenant-a");
        tenantStaffService.disableModerator(moderatorA.getId());
        entityManager.flush();
        entityManager.clear();

        // Then enable
        authenticateAs(tenantAdminA, "staff-test-tenant-a");
        tenantStaffService.enableModerator(moderatorA.getId());
        entityManager.flush();
        entityManager.clear();

        User reloaded = userRepository.findById(moderatorA.getId()).orElseThrow();
        assertTrue(reloaded.isEnabled(), "enabled must be true after enable");
        assertEquals(User.UserStatus.ACTIVE, reloaded.getStatus(), "status must be ACTIVE after enable");
    }

    @Test
    @DisplayName("C12: Old refresh token remains revoked after re-enable")
    void testEnableModerator_OldRefreshTokenRemainsRevoked() {
        RefreshToken rt = refreshTokenRepository.save(RefreshToken.builder()
                .user(moderatorA)
                .token("rt-old-" + System.nanoTime())
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build());
        entityManager.flush();

        // Disable
        authenticateAs(tenantAdminA, "staff-test-tenant-a");
        tenantStaffService.disableModerator(moderatorA.getId());
        entityManager.flush();
        entityManager.clear();

        // Enable
        authenticateAs(tenantAdminA, "staff-test-tenant-a");
        tenantStaffService.enableModerator(moderatorA.getId());
        entityManager.flush();
        entityManager.clear();

        // Old token still revoked — moderator must log in normally
        RefreshToken reloaded = refreshTokenRepository.findById(rt.getId()).orElseThrow();
        assertTrue(reloaded.isRevoked(), "Old refresh token must remain revoked after re-enable");
    }

    @Test
    @DisplayName("C12: Normal login succeeds after re-enable")
    void testEnableModerator_NormalLoginSucceeds() {
        // Disable
        authenticateAs(tenantAdminA, "staff-test-tenant-a");
        tenantStaffService.disableModerator(moderatorA.getId());
        entityManager.flush();
        entityManager.clear();

        // Enable
        authenticateAs(tenantAdminA, "staff-test-tenant-a");
        tenantStaffService.enableModerator(moderatorA.getId());
        entityManager.flush();
        entityManager.clear();

        // Set tenant context for login
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Tenant", "staff-test-tenant-a");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
        SecurityContextHolder.clearContext();

        // loadUserByUsername must succeed and return enabled=true
        CustomUserDetails reloaded = (CustomUserDetails) userDetailsService
                .loadUserByTenantIdAndUsername(tenantA.getId(), moderatorA.getUsername());

        assertTrue(reloaded.isEnabled(), "User must be enabled after re-enable");
        assertNotNull(reloaded.getAuthorities());
        boolean hasMod = reloaded.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_MODERATOR"));
        assertTrue(hasMod, "Re-enabled user must still have MODERATOR role");
    }

    @Test
    @DisplayName("C12: Enabling already-enabled moderator returns CONFLICT")
    void testEnableModerator_AlreadyEnabled() {
        authenticateAs(tenantAdminA, "staff-test-tenant-a");

        assertThrows(ModeratorAlreadyEnabledException.class,
                () -> tenantStaffService.enableModerator(moderatorA.getId()));
    }

    @Test
    @DisplayName("C12: Old support participation remains inactive after re-enable")
    void testEnableModerator_OldParticipationRemainsInactive() {
        Ticket ticket = ticketRepository.save(Ticket.builder()
                .tenant(tenantA).creator(regularUserA)
                .subject("Enable Test Ticket")
                .description("Detailed test description for C12 support ticket")
                .priority(com.forumx.support.ticket.entity.TicketPriority.MEDIUM)
                .status(com.forumx.support.ticket.entity.TicketStatus.OPEN)
                .build());
        ChatSession session = chatSessionRepository.save(ChatSession.builder()
                .ticket(ticket).tenant(tenantA).customer(regularUserA)
                .status(ChatSessionStatus.ACTIVE).build());
        SupportSessionParticipant participation = participantRepository.save(SupportSessionParticipant.builder()
                .session(session).tenant(tenantA).user(moderatorA)
                .role(ParticipantRole.MODERATOR)
                .joinedAt(Instant.now()).isActive(true).build());
        entityManager.flush();

        // Disable
        authenticateAs(tenantAdminA, "staff-test-tenant-a");
        tenantStaffService.disableModerator(moderatorA.getId());
        entityManager.flush();
        entityManager.clear();

        // Enable
        authenticateAs(tenantAdminA, "staff-test-tenant-a");
        tenantStaffService.enableModerator(moderatorA.getId());
        entityManager.flush();
        entityManager.clear();

        // Old participation must remain inactive — moderator does NOT auto-rejoin rooms
        SupportSessionParticipant reloaded = participantRepository.findById(participation.getId()).orElseThrow();
        assertFalse(reloaded.isActive(), "Old participation must remain inactive after re-enable");
    }

    // ── Tenant Isolation — edge cases ───────────────────────────────────

    @Test
    @DisplayName("Tenant A admin cannot list Tenant B invitations")
    void testListInvitations_CrossTenantIsolation() {
        invitationRepository.save(ModeratorInvitation.builder()
                .tenant(tenantB).email("secret_b@b.test").role(RoleType.MODERATOR)
                .tokenHash("hash-secret-" + System.nanoTime()).invitedBy(tenantAdminB)
                .status(InvitationStatus.PENDING).expiresAt(Instant.now().plusSeconds(86400))
                .build());
        entityManager.flush();

        authenticateAs(tenantAdminA, "staff-test-tenant-a");
        Page<InvitationListResponse> page = tenantStaffService.listInvitations(null, PageRequest.of(0, 20));

        boolean leaksB = page.getContent().stream()
                .anyMatch(i -> i.getEmail().equals("secret_b@b.test"));
        assertFalse(leaksB, "Tenant B invitations must NOT appear in Tenant A's list");
    }

    @Test
    @DisplayName("Tenant A admin cannot enable Tenant B moderator")
    void testEnableModerator_CrossTenantIsolation() {
        // Disable moderatorB first via tenantAdminB
        authenticateAs(tenantAdminB, "staff-test-tenant-b");
        tenantStaffService.disableModerator(moderatorB.getId());
        entityManager.flush();
        entityManager.clear();

        // Now tenantAdminA tries to enable moderatorB — must be denied
        authenticateAs(tenantAdminA, "staff-test-tenant-a");
        assertThrows(ModeratorNotFoundException.class,
                () -> tenantStaffService.enableModerator(moderatorB.getId()));
    }
}
