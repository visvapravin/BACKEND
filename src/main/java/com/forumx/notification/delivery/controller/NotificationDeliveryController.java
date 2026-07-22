package com.forumx.notification.delivery.controller;

import com.forumx.notification.delivery.domain.NotificationDeliveryContext;
import com.forumx.notification.delivery.dto.response.NotificationDeliveryAttemptResponse;
import com.forumx.notification.delivery.entity.DeliveryStatus;
import com.forumx.notification.delivery.entity.NotificationDeliveryAttempt;
import com.forumx.notification.delivery.executor.NotificationDeliveryExecutor;
import com.forumx.notification.delivery.repository.NotificationDeliveryAttemptRepository;
import com.forumx.tenant.resolver.TenantResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification Deliveries", description = "Operations for inspecting and managing reliable delivery attempts")
@RestController
@RequestMapping("/api/v1/notification-deliveries")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class NotificationDeliveryController {

    private final NotificationDeliveryAttemptRepository attemptRepository;
    private final NotificationDeliveryExecutor deliveryExecutor;
    private final TenantResolver tenantResolver;

    @Operation(summary = "Get all delivery attempts", description = "Retrieves paginated delivery attempts for the current tenant")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved delivery attempts")
    @GetMapping
    public ResponseEntity<Page<NotificationDeliveryAttemptResponse>> getAllDeliveries(Pageable pageable) {
        Long tenantId = tenantResolver.resolveTenantId();
        Page<NotificationDeliveryAttempt> page = (tenantId != null)
                ? attemptRepository.findByTenant_IdAndDeletedFalse(tenantId, pageable)
                : attemptRepository.findAll(pageable);
        return ResponseEntity.ok(page.map(this::toResponse));
    }

    @Operation(summary = "Get delivery attempts for notification", description = "Retrieves attempt history for a specific notification")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved attempt history")
    @GetMapping("/{notificationId}")
    public ResponseEntity<List<NotificationDeliveryAttemptResponse>> getDeliveriesForNotification(
            @Parameter(description = "Notification ID") @PathVariable Long notificationId) {
        List<NotificationDeliveryAttempt> attempts = attemptRepository.findByNotification_IdOrderByCreatedAtDesc(notificationId);
        return ResponseEntity.ok(attempts.stream().map(this::toResponse).toList());
    }

    @Operation(summary = "Get failed delivery attempts", description = "Retrieves paginated list of failed delivery attempts")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved failed attempts")
    @GetMapping("/failed")
    public ResponseEntity<Page<NotificationDeliveryAttemptResponse>> getFailedDeliveries(Pageable pageable) {
        Long tenantId = tenantResolver.resolveTenantId();
        Page<NotificationDeliveryAttempt> page = (tenantId != null)
                ? attemptRepository.findByTenant_IdAndStatusAndDeletedFalse(tenantId, DeliveryStatus.FAILED, pageable)
                : attemptRepository.findByStatusAndDeletedFalse(DeliveryStatus.FAILED, pageable);
        return ResponseEntity.ok(page.map(this::toResponse));
    }

    @Operation(summary = "Get dead letter delivery attempts", description = "Retrieves paginated list of dead-lettered delivery attempts")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved dead-letter attempts")
    @GetMapping("/dead-letter")
    public ResponseEntity<Page<NotificationDeliveryAttemptResponse>> getDeadLetterDeliveries(Pageable pageable) {
        Long tenantId = tenantResolver.resolveTenantId();
        Page<NotificationDeliveryAttempt> page = (tenantId != null)
                ? attemptRepository.findByTenant_IdAndStatusAndDeletedFalse(tenantId, DeliveryStatus.DEAD_LETTER, pageable)
                : attemptRepository.findByStatusAndDeletedFalse(DeliveryStatus.DEAD_LETTER, pageable);
        return ResponseEntity.ok(page.map(this::toResponse));
    }

    @Operation(summary = "Manually retry delivery attempt", description = "Manually triggers immediate re-delivery of a failed or dead-lettered attempt")
    @ApiResponse(responseCode = "200", description = "Successfully queued manual retry")
    @PostMapping("/{attemptId}/retry")
    public ResponseEntity<NotificationDeliveryAttemptResponse> retryAttempt(
            @Parameter(description = "Attempt ID") @PathVariable Long attemptId) {
        NotificationDeliveryAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery attempt not found: " + attemptId));

        NotificationDeliveryContext context = new NotificationDeliveryContext(
                attempt.getNotification(),
                attempt.getNotification() != null ? attempt.getNotification().getRecipient() : null,
                attempt.getTenant(),
                attempt.getNotification() != null ? attempt.getNotification().getNotificationType() : null,
                attempt.getChannel(),
                attempt.getProvider(),
                1,
                "MANUAL_ADMIN_RETRY",
                attempt.getCorrelationId(),
                null,
                attempt,
                java.util.Map.of()
        );

        NotificationDeliveryAttempt newAttempt = deliveryExecutor.execute(context);
        return ResponseEntity.ok(toResponse(newAttempt));
    }

    private NotificationDeliveryAttemptResponse toResponse(NotificationDeliveryAttempt a) {
        return new NotificationDeliveryAttemptResponse(
                a.getId(),
                a.getTenant() != null ? a.getTenant().getId() : null,
                a.getNotification() != null ? a.getNotification().getId() : null,
                a.getChannel(),
                a.getProvider(),
                a.getStatus(),
                a.getAttemptNumber(),
                a.getCorrelationId(),
                a.getProviderResponse(),
                a.getErrorMessage(),
                a.getStartedAt(),
                a.getCompletedAt(),
                a.getDurationMs(),
                a.getNextRetryAt(),
                a.getCreatedAt()
        );
    }
}
