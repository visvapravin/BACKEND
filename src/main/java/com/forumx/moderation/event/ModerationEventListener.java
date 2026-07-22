package com.forumx.moderation.event;

import com.forumx.answer.entity.Answer;
import com.forumx.answer.repository.AnswerRepository;
import com.forumx.comment.entity.Comment;
import com.forumx.comment.repository.CommentRepository;
import com.forumx.moderation.entity.ModerationReport;
import com.forumx.moderation.entity.ModerationTarget;
import com.forumx.notification.api.NotificationCommand;
import com.forumx.notification.entity.NotificationType;
import com.forumx.notification.entity.ReferenceType;
import com.forumx.notification.service.NotificationApplicationService;
import com.forumx.question.entity.Question;
import com.forumx.question.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModerationEventListener {

    private final NotificationApplicationService notificationService;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final CommentRepository commentRepository;

    @Async
    @EventListener
    public void handleReportCreated(ModerationReportCreatedEvent event) {
        ModerationReport report = event.report();
        log.info("Handling ModerationReportCreatedEvent for reportId={}", report.getId());

        // Notify the Reporter
        NotificationCommand reporterCmd = new NotificationCommand(
                report.getTenant().getId(),
                report.getReporter().getId(),
                report.getReporter().getId(),
                NotificationType.REPORT_CREATED,
                "Report Received",
                "Your report against a " + report.getTarget().getTargetType() + " has been received.",
                ReferenceType.REPORT,
                report.getId()
        );
        notificationService.create(reporterCmd);

        // Notify Content Owner
        Long ownerId = getContentOwnerId(report.getTarget());
        if (ownerId != null) {
            NotificationCommand ownerCmd = new NotificationCommand(
                    report.getTenant().getId(),
                    ownerId,
                    report.getReporter().getId(),
                    NotificationType.CONTENT_REPORTED,
                    "Content Reported",
                    "Your content has been reported for: " + report.getReason(),
                    ReferenceType.REPORT,
                    report.getId()
            );
            notificationService.create(ownerCmd);
        }
    }

    @Async
    @EventListener
    public void handleReportStatusChanged(ModerationReportStatusChangedEvent event) {
        ModerationReport report = event.report();
        log.info("Handling ModerationReportStatusChangedEvent for reportId={}, old={}, new={}", 
                report.getId(), event.previousStatus(), event.newStatus());

        if (event.newStatus() == com.forumx.moderation.entity.ReportStatus.IN_REVIEW) {
            // Notify Reporter
            if (report.getReporter() != null) {
                NotificationCommand reporterCmd = new NotificationCommand(
                        report.getTenant().getId(),
                        report.getReporter().getId(),
                        report.getAssignedTo() != null ? report.getAssignedTo().getId() : null,
                        NotificationType.REPORT_ASSIGNED,
                        "Report Under Review",
                        "Your report is now under review by a moderator.",
                        ReferenceType.REPORT,
                        report.getId()
                );
                notificationService.create(reporterCmd);
            }

            // Notify Assigned Moderator
            if (report.getAssignedTo() != null) {
                NotificationCommand modCmd = new NotificationCommand(
                        report.getTenant().getId(),
                        report.getAssignedTo().getId(),
                        report.getReporter() != null ? report.getReporter().getId() : null,
                        NotificationType.REPORT_ASSIGNED,
                        "Report Assigned",
                        "Moderation report " + report.getId() + " has been assigned to you.",
                        ReferenceType.REPORT,
                        report.getId()
                );
                notificationService.create(modCmd);
            }
        }
    }

    @Async
    @EventListener
    public void handleDecisionApplied(ModerationDecisionAppliedEvent event) {
        ModerationReport report = event.report();
        log.info("Handling ModerationDecisionAppliedEvent for reportId={}, action={}", report.getId(), event.action());

        NotificationType notificationType = event.action() == com.forumx.moderation.entity.ModerationAction.DISMISS
                ? NotificationType.REPORT_REJECTED 
                : NotificationType.REPORT_RESOLVED;

        String title = event.action() == com.forumx.moderation.entity.ModerationAction.DISMISS ? "Report Dismissed" : "Report Resolved";
        String message = event.action() == com.forumx.moderation.entity.ModerationAction.DISMISS
                ? "Your report has been reviewed and dismissed by a moderator."
                : "Your report has been resolved. Action taken: " + event.action();

        // Notify Reporter
        if (report.getReporter() != null) {
            NotificationCommand reporterCmd = new NotificationCommand(
                    report.getTenant().getId(),
                    report.getReporter().getId(),
                    event.moderatorId(),
                    notificationType,
                    title,
                    message,
                    ReferenceType.REPORT,
                    report.getId()
            );
            notificationService.create(reporterCmd);
        }

        // Notify Content Owner if action applied changes content state
        if (event.action() != com.forumx.moderation.entity.ModerationAction.DISMISS) {
            Long ownerId = getContentOwnerId(report.getTarget());
            if (ownerId != null) {
                NotificationCommand ownerCmd = new NotificationCommand(
                        report.getTenant().getId(),
                        ownerId,
                        event.moderatorId(),
                        NotificationType.REPORT_RESOLVED,
                        "Moderation Action Applied",
                        "Moderation action taken on your content: " + event.action() + ". Notes: " + event.decisionNotes(),
                        ReferenceType.REPORT,
                        report.getId()
                );
                notificationService.create(ownerCmd);
            }
        }
    }

    private Long getContentOwnerId(ModerationTarget target) {
        try {
            switch (target.getTargetType()) {
                case QUESTION -> {
                    Question q = questionRepository.findById(target.getTargetId()).orElse(null);
                    if (q != null && q.getAuthor() != null) {
                        return q.getAuthor().getId();
                    }
                }
                case ANSWER -> {
                    Answer a = answerRepository.findById(target.getTargetId()).orElse(null);
                    if (a != null && a.getAuthor() != null) {
                        return a.getAuthor().getId();
                    }
                }
                case COMMENT -> {
                    Comment c = commentRepository.findById(target.getTargetId()).orElse(null);
                    if (c != null && c.getAuthor() != null) {
                        return c.getAuthor().getId();
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to retrieve content owner for targetType={} targetId={}", 
                    target.getTargetType(), target.getTargetId(), e);
        }
        return null;
    }
}
