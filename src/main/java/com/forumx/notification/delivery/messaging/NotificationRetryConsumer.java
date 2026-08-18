package com.forumx.notification.delivery.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forumx.auth.entity.User;
import com.forumx.messaging.constant.MessagingQueues;
import com.forumx.messaging.dto.EventEnvelope;
import com.forumx.notification.delivery.domain.NotificationDeliveryContext;
import com.forumx.notification.delivery.dto.message.NotificationRetryPayload;
import com.forumx.notification.delivery.executor.NotificationDeliveryExecutor;
import com.forumx.notification.delivery.repository.NotificationDeliveryAttemptRepository;
import com.forumx.notification.entity.Notification;
import com.forumx.notification.repository.NotificationRepository;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "forumx.messaging.enabled", havingValue = "true")
public class NotificationRetryConsumer {

    private final NotificationDeliveryExecutor deliveryExecutor;
    private final NotificationRepository notificationRepository;
    private final TenantRepository tenantRepository;
    private final NotificationDeliveryAttemptRepository attemptRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = MessagingQueues.DELIVERY_RETRY_QUEUE)
    public void onRetryEvent(EventEnvelope<?> envelope) {
        log.info("Received delivery retry event: eventId={} type={}", envelope.eventId(), envelope.eventType());
        try {
            NotificationRetryPayload payload = objectMapper.convertValue(envelope.payload(), NotificationRetryPayload.class);
            if (payload == null) {
                log.error("Failed to parse retry payload from envelope: {}", envelope);
                return;
            }

            Notification notification = null;
            Tenant tenant = null;
            User recipient = null;

            if (payload.notificationId() != null) {
                notification = notificationRepository.findById(payload.notificationId()).orElse(null);
                if (notification != null) {
                    tenant = notification.getTenant();
                    recipient = notification.getRecipient();
                }
            }

            if (tenant == null && envelope.tenantId() != null) {
                tenant = tenantRepository.findById(envelope.tenantId()).orElse(null);
            }

            var previousAttempt = attemptRepository.findByTenant_IdAndNotification_IdOrderByCreatedAtDesc(
                    envelope.tenantId(), payload.notificationId()
            ).stream().findFirst().orElse(null);

            NotificationDeliveryContext context = new NotificationDeliveryContext(
                    notification,
                    recipient,
                    tenant,
                    notification != null ? notification.getNotificationType() : null,
                    payload.channel(),
                    previousAttempt != null ? previousAttempt.getProvider() : null,
                    1,
                    "RETRY",
                    payload.correlationId(),
                    null,
                    previousAttempt,
                    java.util.Map.of()
            );

            deliveryExecutor.execute(context);
        } catch (Exception e) {
            log.error("Error processing delivery retry event: {}", e.getMessage(), e);
        }
    }
}
