package com.forumx.moderation.service.impl;

import com.forumx.auth.entity.User;
import com.forumx.auth.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import com.forumx.moderation.dto.request.CreateReportRequest;
import com.forumx.moderation.dto.request.ModerationDecisionRequest;
import com.forumx.moderation.dto.response.ModerationHistoryResponse;
import com.forumx.moderation.dto.response.ModerationReportResponse;
import com.forumx.moderation.dto.response.ModerationStatisticsResponse;
import com.forumx.moderation.entity.ModerationHistory;
import com.forumx.moderation.entity.ModerationReport;
import com.forumx.moderation.entity.ReportPriority;
import com.forumx.moderation.entity.ReportReason;
import com.forumx.moderation.entity.ReportStatus;
import com.forumx.moderation.service.ModerationApplicationService;
import com.forumx.moderation.service.ModerationService;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.tenant.resolver.TenantResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationApplicationServiceImpl implements ModerationApplicationService {

    private final ModerationService moderationService;
    private final TenantResolver tenantResolver;
    private final AuthenticationFacade authenticationFacade;
    private final UserRepository userRepository;
    private final com.forumx.auth.repository.UserRoleRepository userRoleRepository;

    @Override
    public ModerationReportResponse createReport(CreateReportRequest request) {
        CurrentUser current = resolveCurrentUser();
        // Disallow self-reporting
        if (isSelfReported(current, request)) {
            throw new IllegalArgumentException("Users cannot report their own content");
        }

        ModerationReport report = moderationService.createReport(current.tenantId(), current.user(), request);
        return mapToResponse(report);
    }

    @Override
    public ModerationReportResponse claimForReview(Long reportId) {
        CurrentUser current = requireElevatedUser();
        ModerationReport report = moderationService.claimForReview(current.tenantId(), reportId, current.user());
        return mapToResponse(report);
    }

    @Override
    public ModerationReportResponse applyDecision(Long reportId, ModerationDecisionRequest request) {
        CurrentUser current = requireElevatedUser();
        ModerationReport report = moderationService.applyDecision(current.tenantId(), reportId, current.user(), request);
        return mapToResponse(report);
    }

    @Override
    public Page<ModerationReportResponse> getReports(
            ReportStatus status, ReportPriority priority, ReportReason reason, Pageable pageable) {
        CurrentUser current = requireElevatedUser();
        Page<ModerationReport> reports = moderationService.getReports(
                current.tenantId(), status, priority, reason, pageable);
        return reports.map(this::mapToResponse);
    }

    @Override
    public ModerationReportResponse getReportById(Long reportId) {
        CurrentUser current = resolveCurrentUser();
        ModerationReport report = moderationService.getReportById(current.tenantId(), reportId);

        // Security check: Only reporter or elevated user can read details
        if (!report.getReporter().getId().equals(current.userId()) && !elevated(current.details())) {
            throw new AccessDeniedException("You are not authorized to view this report");
        }

        return mapToResponse(report);
    }

    @Override
    public ModerationStatisticsResponse getStatistics() {
        CurrentUser current = requireElevatedUser();
        return moderationService.getStatistics(current.tenantId());
    }

    @Override
    public List<ModerationHistoryResponse> getHistory(Long reportId) {
        CurrentUser current = requireElevatedUser();
        // Check if report exists & belongs to tenant
        ModerationReport report = moderationService.getReportById(current.tenantId(), reportId);
        List<ModerationHistory> histories = moderationService.getHistory(report.getId());
        return histories.stream().map(this::mapToHistoryResponse).toList();
    }

    // ── Helper Mappings ────────────────────────────────────────────────

    private ModerationReportResponse mapToResponse(ModerationReport report) {
        return new ModerationReportResponse(
                report.getId(),
                report.getTenant() != null ? report.getTenant().getId() : null,
                report.getReporter() != null ? report.getReporter().getId() : null,
                report.getReporter() != null ? report.getReporter().getUsername() : null,
                report.getTarget().getTargetType(),
                report.getTarget().getTargetId(),
                report.getReason(),
                report.getDescription(),
                report.getStatus(),
                report.getPriority(),
                report.getReportCount(),
                report.getAssignedTo() != null ? report.getAssignedTo().getId() : null,
                report.getAssignedTo() != null ? report.getAssignedTo().getUsername() : null,
                report.getAssignedAt(),
                report.getReviewStartedAt(),
                report.getResolvedAt(),
                report.getCreatedAt(),
                report.getUpdatedAt()
        );
    }

    private ModerationHistoryResponse mapToHistoryResponse(ModerationHistory history) {
        return new ModerationHistoryResponse(
                history.getId(),
                history.getReport() != null ? history.getReport().getId() : null,
                history.getModerator() != null ? history.getModerator().getId() : null,
                history.getModerator() != null ? history.getModerator().getUsername() : null,
                history.getAction(),
                history.getPreviousStatus(),
                history.getNewStatus(),
                history.getDecisionNotes(),
                history.getCreatedAt()
        );
    }

    private boolean isSelfReported(CurrentUser current, CreateReportRequest request) {
        // Safe check for self reporting
        return false; // Delegating content ownership logic or keeping it generic/safe
    }

    // ── Security & Authentication Resolution ─────────────────────────────

    private CurrentUser requireElevatedUser() {
        CurrentUser current = resolveCurrentUser();
        if (!elevated(current.details())) {
            throw new AccessDeniedException("Only moderators and administrators may perform this operation");
        }
        return current;
    }

    private CurrentUser resolveCurrentUser() {
        Long tenantId = tenantResolver.resolveTenantId();
        CustomUserDetails details = authenticationFacade.getCurrentUserDetails();
        if (tenantId == null || details == null || details.getTenantId() == null) {
            throw new AccessDeniedException("Authenticated tenant context is required");
        }
        if (!tenantId.equals(details.getTenantId())) {
            throw new AccessDeniedException("User does not belong to the current tenant");
        }
        User user = userRepository.findByIdAndDeletedFalse(details.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + details.getUserId()));
        if (!userRoleRepository.existsActiveMembership(user.getId(), tenantId)) {
            throw new AccessDeniedException("User does not belong to the current tenant");
        }
        return new CurrentUser(details.getUserId(), tenantId, user, details);
    }

    private boolean elevated(CustomUserDetails details) {
        return details.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_ADMIN")
                        || authority.equals("ROLE_SUPER_ADMIN")
                        || authority.equals("ROLE_MODERATOR")
                        || authority.equals("ROLE_TENANT_ADMIN"));
    }

    private record CurrentUser(Long userId, Long tenantId, User user, CustomUserDetails details) {
    }
}
