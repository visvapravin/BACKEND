package com.forumx.moderation.service.impl;

import com.forumx.auth.entity.User;
import jakarta.persistence.EntityNotFoundException;
import com.forumx.moderation.dto.request.CreateReportRequest;
import com.forumx.moderation.dto.request.ModerationDecisionRequest;
import com.forumx.moderation.dto.response.ModerationStatisticsResponse;
import com.forumx.moderation.engine.ModerationDecisionEngine;
import com.forumx.moderation.engine.ModerationStateMachine;
import com.forumx.moderation.engine.executor.ModerationActionExecutor;
import com.forumx.moderation.entity.ModerationHistory;
import com.forumx.moderation.entity.ModerationReport;
import com.forumx.moderation.entity.ModerationTarget;
import com.forumx.moderation.entity.ReportPriority;
import com.forumx.moderation.entity.ReportReason;
import com.forumx.moderation.entity.ReportStatus;
import com.forumx.moderation.event.ModerationDecisionAppliedEvent;
import com.forumx.moderation.event.ModerationReportCreatedEvent;
import com.forumx.moderation.event.ModerationReportStatusChangedEvent;
import com.forumx.moderation.exception.InvalidModerationStateException;
import com.forumx.moderation.repository.ModerationHistoryRepository;
import com.forumx.moderation.repository.ModerationReportRepository;
import com.forumx.moderation.service.ModerationService;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.persistence.criteria.Predicate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationServiceImpl implements ModerationService {

    private final ModerationReportRepository reportRepository;
    private final ModerationHistoryRepository historyRepository;
    private final TenantRepository tenantRepository;
    private final ModerationStateMachine stateMachine;
    private final ModerationDecisionEngine decisionEngine;
    private final ModerationActionExecutor actionExecutor;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    @Override
    @Transactional
    public ModerationReport createReport(Long tenantId, User reporter, CreateReportRequest request) {
        log.info("Creating moderation report for tenantId={}, targetId={}, targetType={}", 
                tenantId, request.targetId(), request.targetType());

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found with id: " + tenantId));

        // Check duplicate report aggregation
        Optional<ModerationReport> existingReportOpt = reportRepository
                .findByTenant_IdAndTarget_TargetTypeAndTarget_TargetIdAndReasonAndStatusIn(
                        tenantId,
                        request.targetType(),
                        request.targetId(),
                        request.reason(),
                        List.of(ReportStatus.OPEN, ReportStatus.IN_REVIEW)
                );

        ModerationReport report;
        if (existingReportOpt.isPresent()) {
            report = existingReportOpt.get();
            report.setReportCount(report.getReportCount() + 1);
            report.setDescription(report.getDescription() + "\n[Duplicate Report]: " + request.description());
            report = reportRepository.save(report);
            log.info("Aggregated duplicate report into reportId={}, new reportCount={}", report.getId(), report.getReportCount());
        } else {
            ReportPriority priority = decisionEngine.evaluatePriority(request.reason());
            ModerationTarget target = ModerationTarget.builder()
                    .targetType(request.targetType())
                    .targetId(request.targetId())
                    .build();

            report = ModerationReport.builder()
                    .tenant(tenant)
                    .reporter(reporter)
                    .target(target)
                    .reason(request.reason())
                    .description(request.description())
                    .status(ReportStatus.OPEN)
                    .priority(priority)
                    .reportCount(1)
                    .build();
            report = reportRepository.save(report);
            log.info("Created new moderation report with id={}", report.getId());
        }

        // Record Micrometer metric
        meterRegistry.counter("forumx.moderation.reports",
                "tenant", String.valueOf(tenantId),
                "reason", request.reason().name(),
                "priority", report.getPriority().name()).increment();

        eventPublisher.publishEvent(new ModerationReportCreatedEvent(report));
        return report;
    }

    @Override
    @Transactional
    public ModerationReport claimForReview(Long tenantId, Long reportId, User moderator) {
        log.info("Claiming reportId={} for review by moderatorId={}", reportId, moderator.getId());

        ModerationReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new EntityNotFoundException("Report not found with id: " + reportId));

        if (!report.getTenant().getId().equals(tenantId)) {
            throw new EntityNotFoundException("Report not found with id: " + reportId);
        }

        ReportStatus previousStatus = report.getStatus();
        stateMachine.validateTransition(previousStatus, ReportStatus.IN_REVIEW);

        report.setStatus(ReportStatus.IN_REVIEW);
        report.setAssignedTo(moderator);
        report.setAssignedAt(Instant.now());
        report.setReviewStartedAt(Instant.now());

        report = reportRepository.save(report);

        // Record history log (Audit trail)
        ModerationHistory history = ModerationHistory.builder()
                .tenant(report.getTenant())
                .report(report)
                .moderator(moderator)
                .action(com.forumx.moderation.entity.ModerationAction.DISMISS) // Initial step placeholder
                .previousStatus(previousStatus)
                .newStatus(ReportStatus.IN_REVIEW)
                .decisionNotes("Report claimed for review")
                .build();
        historyRepository.save(history);

        eventPublisher.publishEvent(new ModerationReportStatusChangedEvent(report, previousStatus, ReportStatus.IN_REVIEW));
        return report;
    }

    @Override
    @Transactional
    public ModerationReport applyDecision(Long tenantId, Long reportId, User moderator, ModerationDecisionRequest request) {
        log.info("Applying decision action={} on reportId={} by moderatorId={}", 
                request.action(), reportId, moderator.getId());

        ModerationReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new EntityNotFoundException("Report not found with id: " + reportId));

        if (!report.getTenant().getId().equals(tenantId)) {
            throw new EntityNotFoundException("Report not found with id: " + reportId);
        }

        // Validate legality of action using decision engine
        if (!decisionEngine.isActionAllowed(report.getReason(), request.action())) {
            throw new InvalidModerationStateException(
                    "Action " + request.action() + " is not allowed for report reason " + report.getReason());
        }

        ReportStatus previousStatus = report.getStatus();
        ReportStatus newStatus = request.action() == com.forumx.moderation.entity.ModerationAction.DISMISS 
                ? ReportStatus.REJECTED 
                : ReportStatus.RESOLVED;

        stateMachine.validateTransition(previousStatus, newStatus);

        // Transition states
        report.setStatus(newStatus);
        report.setResolvedAt(Instant.now());
        report = reportRepository.save(report);

        // Execute action side effects
        actionExecutor.execute(tenantId, request.action(), report.getTarget(), request.decisionNotes());

        // Append to immutable history trail
        ModerationHistory history = ModerationHistory.builder()
                .tenant(report.getTenant())
                .report(report)
                .moderator(moderator)
                .action(request.action())
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .decisionNotes(request.decisionNotes())
                .build();
        historyRepository.save(history);

        // Record metrics
        meterRegistry.counter("forumx.moderation.decisions",
                "tenant", String.valueOf(tenantId),
                "action", request.action().name(),
                "moderator", moderator.getUsername()).increment();

        if (report.getCreatedAt() != null) {
            Duration duration = Duration.between(report.getCreatedAt(), Instant.now());
            Timer.builder("forumx.moderation.duration")
                    .tag("tenant", String.valueOf(tenantId))
                    .register(meterRegistry)
                    .record(duration);
        }

        eventPublisher.publishEvent(new ModerationDecisionAppliedEvent(report, moderator.getId(), request.action(), request.decisionNotes()));
        eventPublisher.publishEvent(new ModerationReportStatusChangedEvent(report, previousStatus, newStatus));

        return report;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ModerationReport> getReports(
            Long tenantId, ReportStatus status, ReportPriority priority, ReportReason reason, Pageable pageable) {

        Specification<ModerationReport> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("deleted"), false));

            if (tenantId != null) {
                predicates.add(cb.equal(root.get("tenant").get("id"), tenantId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (reason != null) {
                predicates.add(cb.equal(root.get("reason"), reason));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return reportRepository.findAll(spec, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public ModerationReport getReportById(Long tenantId, Long reportId) {
        ModerationReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new EntityNotFoundException("Report not found with id: " + reportId));

        if (!report.getTenant().getId().equals(tenantId)) {
            throw new EntityNotFoundException("Report not found with id: " + reportId);
        }

        return report;
    }

    @Override
    @Transactional(readOnly = true)
    public ModerationStatisticsResponse getStatistics(Long tenantId) {
        long openCount = reportRepository.countByTenant_IdAndStatus(tenantId, ReportStatus.OPEN);
        long closedCount = reportRepository.countByTenant_IdAndStatusIn(tenantId, List.of(ReportStatus.RESOLVED, ReportStatus.REJECTED));

        List<ModerationReport> allReports = reportRepository.findAll((root, query, cb) -> 
            cb.and(
                cb.equal(root.get("tenant").get("id"), tenantId),
                cb.equal(root.get("deleted"), false)
            )
        );

        double totalResolutionTimeMinutes = 0;
        long resolvedCount = 0;

        Map<String, Long> reportsByReason = new HashMap<>();
        Map<String, Long> reportsByModerator = new HashMap<>();
        Map<String, Long> reportsByTenant = new HashMap<>();

        for (ModerationReport r : allReports) {
            reportsByReason.merge(r.getReason().name(), 1L, Long::sum);
            if (r.getTenant() != null) {
                reportsByTenant.merge(String.valueOf(r.getTenant().getId()), 1L, Long::sum);
            }
            if (r.getAssignedTo() != null) {
                reportsByModerator.merge(r.getAssignedTo().getUsername(), 1L, Long::sum);
            }

            if (r.getResolvedAt() != null && r.getCreatedAt() != null) {
                Duration d = Duration.between(r.getCreatedAt(), r.getResolvedAt());
                totalResolutionTimeMinutes += d.toMinutes();
                resolvedCount++;
            }
        }

        double avgResolutionTime = resolvedCount > 0 ? (totalResolutionTimeMinutes / resolvedCount) : 0.0;

        return new ModerationStatisticsResponse(
                openCount,
                closedCount,
                avgResolutionTime,
                reportsByReason,
                reportsByModerator,
                reportsByTenant
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ModerationHistory> getHistory(Long reportId) {
        return historyRepository.findByReport_IdOrderByCreatedAtDesc(reportId);
    }
}
