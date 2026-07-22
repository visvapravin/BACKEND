package com.forumx.notification.dispatcher;

import com.forumx.notification.delivery.domain.NotificationDeliveryContext;
import com.forumx.notification.delivery.executor.NotificationDeliveryExecutor;
import com.forumx.notification.entity.NotificationType;
import com.forumx.notification.preference.dto.NotificationDeliveryDecision;
import com.forumx.notification.preference.dto.response.NotificationPreferenceResponse;
import com.forumx.notification.preference.engine.NotificationDecisionEngine;
import com.forumx.notification.preference.service.NotificationPreferenceService;
import com.forumx.presence.dto.UserPresence;
import com.forumx.presence.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final PresenceService presenceService;
    private final NotificationPreferenceService preferenceService;
    private final NotificationDecisionEngine decisionEngine;
    private final NotificationDeliveryExecutor deliveryExecutor;

    public void dispatch(NotificationDeliveryRequest request) {
        if (request == null) {
            log.warn("Cannot dispatch null NotificationDeliveryRequest");
            return;
        }

        Long tenantId = resolveTenantId(request);
        Long recipientId = resolveRecipientId(request);
        NotificationType type = resolveNotificationType(request);

        // 1. Query Presence & Preferences
        UserPresence presence = recipientId != null ? presenceService.getPresence(recipientId).orElse(null) : null;
        NotificationPreferenceResponse preference = (tenantId != null && recipientId != null && type != null)
                ? preferenceService.getEffectivePreference(tenantId, recipientId, type)
                : null;

        // 2. Evaluate Decision Engine
        NotificationDeliveryDecision decision = decisionEngine.evaluate(
                request.notification(),
                request.event(),
                presence,
                preference
        );

        log.info("Smart Notification Decision for recipientId={}: channels={}, reasons={}",
                recipientId, decision.enabledChannels(), decision.reasons());

        // 3. Delegate Reliable Execution to NotificationDeliveryExecutor per channel
        if (decision.enabledChannels() != null) {
            for (com.forumx.notification.preference.domain.DeliveryChannel preferenceChannel : decision.enabledChannels()) {
                com.forumx.notification.delivery.entity.DeliveryChannel deliveryChannel;
                try {
                    deliveryChannel = com.forumx.notification.delivery.entity.DeliveryChannel.valueOf(preferenceChannel.name());
                } catch (IllegalArgumentException e) {
                    log.warn("Unsupported delivery channel for execution: {}", preferenceChannel);
                    continue;
                }

                com.forumx.notification.delivery.entity.DeliveryProvider provider = (deliveryChannel == com.forumx.notification.delivery.entity.DeliveryChannel.EMAIL)
                        ? com.forumx.notification.delivery.entity.DeliveryProvider.SMTP
                        : com.forumx.notification.delivery.entity.DeliveryProvider.WEBSOCKET;

                NotificationDeliveryContext context = new NotificationDeliveryContext(
                        request.notification(),
                        request.notification() != null ? request.notification().getRecipient() : null,
                        request.notification() != null ? request.notification().getTenant() : null,
                        request.notification() != null ? request.notification().getNotificationType() : null,
                        deliveryChannel,
                        provider,
                        1,
                        "SMART_DISPATCH",
                        null,
                        null,
                        null,
                        java.util.Map.of()
                );
                deliveryExecutor.execute(context);
            }
        }
    }

    private Long resolveTenantId(NotificationDeliveryRequest request) {
        if (request.notification() != null && request.notification().getTenant() != null) {
            return request.notification().getTenant().getId();
        }
        if (request.event() != null) {
            return request.event().tenantId();
        }
        return null;
    }

    private Long resolveRecipientId(NotificationDeliveryRequest request) {
        if (request.notification() != null && request.notification().getRecipient() != null) {
            return request.notification().getRecipient().getId();
        }
        if (request.event() != null) {
            return request.event().userId();
        }
        return null;
    }

    private NotificationType resolveNotificationType(NotificationDeliveryRequest request) {
        if (request.notification() != null && request.notification().getNotificationType() != null) {
            return request.notification().getNotificationType();
        }
        if (request.event() != null && request.event().type() != null) {
            try {
                return NotificationType.valueOf(request.event().type());
            } catch (Exception ignored) {}
        }
        return NotificationType.SYSTEM;
    }
}
