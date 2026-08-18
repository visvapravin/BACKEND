package com.forumx.moderation;

import static org.junit.jupiter.api.Assertions.*;

import com.forumx.ForumXApplication;
import com.forumx.auth.entity.Role;
import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserRole;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.repository.RoleRepository;
import com.forumx.auth.repository.UserRepository;
import com.forumx.auth.repository.UserRoleRepository;
import com.forumx.moderation.dto.request.CreateReportRequest;
import com.forumx.moderation.dto.request.ModerationDecisionRequest;
import com.forumx.moderation.dto.response.ModerationReportResponse;
import com.forumx.moderation.dto.response.ModerationStatisticsResponse;
import com.forumx.moderation.entity.ModerationAction;
import com.forumx.moderation.entity.ModerationTargetType;
import com.forumx.moderation.entity.ReportPriority;
import com.forumx.moderation.entity.ReportReason;
import com.forumx.moderation.entity.ReportStatus;
import com.forumx.moderation.exception.InvalidModerationStateException;
import com.forumx.moderation.repository.ModerationHistoryRepository;
import com.forumx.moderation.repository.ModerationReportRepository;
import com.forumx.moderation.service.ModerationApplicationService;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import com.forumx.security.model.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("dev")
@SpringBootTest(classes = ForumXApplication.class)
@Transactional
public class ModerationIntegrationTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private ModerationReportRepository reportRepository;
    @Autowired private ModerationHistoryRepository historyRepository;
    @Autowired private ModerationApplicationService appService;

    @org.springframework.boot.test.mock.mockito.MockBean private com.forumx.messaging.gateway.EventGateway eventGateway;
    @org.springframework.boot.test.mock.mockito.MockBean private org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;
    @org.springframework.boot.test.mock.mockito.MockBean private org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory;

    private Tenant tenant;
    private User normalUser;
    private User moderatorUser;

    @BeforeEach
    public void setUp() {
        historyRepository.deleteAll();
        reportRepository.deleteAll();

        // 1. Create/Find Tenant
        tenant = tenantRepository.findBySlug("default")
                .orElseGet(() -> tenantRepository.save(
                        Tenant.builder()
                                .name("Default Tenant")
                                .slug("default")
                                .description("Default Tenant Description")
                                .build()
                ));

        // Delete users if existing to avoid unique constraints
        String normalUsername = "normal_" + System.currentTimeMillis();
        String modUsername = "mod_" + System.currentTimeMillis();

        // 2. Create Users
        normalUser = User.builder()
                .tenant(tenant)
                .username(normalUsername)
                .email(normalUsername + "@test.com")
                .passwordHash("password")
                .enabled(true)
                .build();
        normalUser = userRepository.save(normalUser);

        moderatorUser = User.builder()
                .tenant(tenant)
                .username(modUsername)
                .email(modUsername + "@test.com")
                .passwordHash("password")
                .enabled(true)
                .build();
        moderatorUser = userRepository.save(moderatorUser);
    }

    private void authenticateAs(User user, RoleType roleType) {
        Role role = roleRepository.findByRoleName(roleType)
                .orElseGet(() -> roleRepository.save(Role.builder().roleName(roleType).build()));

        UserRole userRole = UserRole.builder()
                .user(user)
                .role(role)
                .active(true)
                .build();
        userRoleRepository.save(userRole);

        user.getUserRoles().add(userRole);
        user = userRepository.save(user);

        CustomUserDetails details = new CustomUserDetails(user);
        var auth = new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    public void testFullWorkflowAndStatistics() {
        // 1. Create Report as Normal User
        authenticateAs(normalUser, RoleType.USER);

        CreateReportRequest createReq = new CreateReportRequest(
                ModerationTargetType.QUESTION, 99L, ReportReason.SPAM, "Spam content description");

        ModerationReportResponse created = appService.createReport(createReq);
        assertNotNull(created);
        assertEquals(ReportStatus.OPEN, created.status());
        assertEquals(ReportPriority.LOW, created.priority()); // Spam evaluates to LOW
        assertEquals(1, created.reportCount());

        // 2. Duplicate Report Aggregation
        ModerationReportResponse aggregated = appService.createReport(createReq);
        assertEquals(created.id(), aggregated.id());
        assertEquals(2, aggregated.reportCount());

        // 3. Claim for Review as Moderator
        authenticateAs(moderatorUser, RoleType.MODERATOR);
        ModerationReportResponse claimed = appService.claimForReview(created.id());
        assertEquals(ReportStatus.IN_REVIEW, claimed.status());
        assertEquals(moderatorUser.getId(), claimed.assignedToId());
        assertNotNull(claimed.assignedAt());

        // 4. Invalid State Transition: Try claiming again
        assertThrows(InvalidModerationStateException.class, () -> {
            appService.claimForReview(created.id());
        });

        // 5. Apply Decision
        ModerationDecisionRequest decisionReq = new ModerationDecisionRequest(
                ModerationAction.DISMISS, "Dismissing report as it is spam but resolved");
        ModerationReportResponse resolved = appService.applyDecision(created.id(), decisionReq);
        assertEquals(ReportStatus.REJECTED, resolved.status()); // Dismiss maps to REJECTED
        assertNotNull(resolved.resolvedAt());

        // 6. Verify audit history
        var history = appService.getHistory(created.id());
        assertNotNull(history);
        assertFalse(history.isEmpty());
        assertEquals(ModerationAction.DISMISS, history.get(0).action());

        // 7. Get Statistics
        ModerationStatisticsResponse stats = appService.getStatistics();
        assertNotNull(stats);
        assertEquals(0, stats.openReports());
        assertEquals(1, stats.closedReports());
    }
}
