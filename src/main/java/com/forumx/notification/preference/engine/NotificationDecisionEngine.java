package com.forumx.notification.preference.engine;

import java.util.EnumSet;

import com.forumx.notification.dto.NotificationEvent;
import com.forumx.notification.entity.Notification;
import com.forumx.notification.entity.NotificationType;
import com.forumx.notification.preference.domain.DecisionReason;
import com.forumx.notification.preference.domain.DeliveryChannel;
import com.forumx.notification.preference.domain.NotificationPriority;
import com.forumx.notification.preference.dto.NotificationDeliveryDecision;
import com.forumx.notification.preference.dto.response.NotificationPreferenceResponse;
import com.forumx.presence.dto.UserPresence;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Pure, stateless decision engine for evaluating multi-channel notification delivery. */
@Slf4j
@Component
public class NotificationDecisionEngine {

    public NotificationDeliveryDecision evaluate(
            Notification notification,
            NotificationEvent event,
            UserPresence userPresence,
            NotificationPreferenceResponse preference) {

        EnumSet<DeliveryChannel> enabledChannels = EnumSet.noneOf(DeliveryChannel.class);
        EnumSet<DecisionReason> reasons = EnumSet.noneOf(DecisionReason.class);

        NotificationType type = resolveNotificationType(notification, event, preference);
        NotificationPriority priority = resolvePriority(type);

        // Rule 1: Critical Transactional Notifications ALWAYS send Email regardless of user online state/preferences
        if (priority == NotificationPriority.CRITICAL || type == NotificationType.EMAIL_VERIFICATION || type == NotificationType.PASSWORD_RESET) {
            enabledChannels.add(DeliveryChannel.EMAIL);
            reasons.add(DecisionReason.CRITICAL_TRANSACTIONAL_EMAIL_FORCED);
        }

        boolean isOnline = userPresence != null && userPresence.getStatus() == com.forumx.presence.dto.PresenceStatus.ONLINE;
        boolean webSocketPref = preference == null || preference.webSocketEnabled();
        boolean emailPref = preference == null || preference.emailEnabled();
        boolean pushPref = preference != null && preference.pushEnabled();
        boolean digestPref = preference != null && preference.digestEnabled();

        // Rule 2: User Online -> Send WebSocket if preference enabled
        if (isOnline) {
            if (webSocketPref) {
                enabledChannels.add(DeliveryChannel.WEBSOCKET);
                reasons.add(DecisionReason.USER_ONLINE_WEBSOCKET_ENABLED);
            } else if (emailPref && !enabledChannels.contains(DeliveryChannel.EMAIL)) {
                // Fallback to Email if WebSocket preference is disabled
                enabledChannels.add(DeliveryChannel.EMAIL);
                reasons.add(DecisionReason.USER_ONLINE_FALLBACK_TO_EMAIL);
            }
        } else {
            // Rule 3: User Offline -> Send Email if preference enabled
            reasons.add(DecisionReason.USER_OFFLINE_WEBSOCKET_DISABLED);
            if (emailPref && !enabledChannels.contains(DeliveryChannel.EMAIL)) {
                enabledChannels.add(DeliveryChannel.EMAIL);
                reasons.add(DecisionReason.USER_OFFLINE_EMAIL_ENABLED);
            }
        }

        // Rule 4: Push Notifications Stub
        if (pushPref) {
            enabledChannels.add(DeliveryChannel.PUSH);
            reasons.add(DecisionReason.PUSH_ENABLED);
        }

        // Rule 5: Digest Notifications Stub
        if (digestPref) {
            enabledChannels.add(DeliveryChannel.DIGEST);
            reasons.add(DecisionReason.DIGEST_ENABLED);
        }

        log.debug("Evaluated notification delivery. type={}, priority={}, online={}, channels={}, reasons={}",
                type, priority, isOnline, enabledChannels, reasons);

        return new NotificationDeliveryDecision(enabledChannels, reasons);
    }

    public NotificationPriority resolvePriority(NotificationType type) {
        if (type == null) {
            return NotificationPriority.NORMAL;
        }
        return switch (type) {
            case EMAIL_VERIFICATION, PASSWORD_RESET -> NotificationPriority.CRITICAL;
            case SYSTEM, TICKET_CREATED, TICKET_MESSAGE, CONTENT_REPORTED -> NotificationPriority.HIGH;
            case NEW_QUESTION, NEW_ANSWER, NEW_COMMENT, NEW_REPLY, NEW_MENTION, ANSWER_CREATED, QUESTION_COMMENTED, QUESTION_MENTION, ANSWER_MENTION, REPORT_CREATED, REPORT_ASSIGNED, REPORT_RESOLVED, REPORT_REJECTED -> NotificationPriority.NORMAL;
            case NEW_VOTE, VOTE_RECEIVED, QUESTION_UPVOTED, ANSWER_UPVOTED, COMMENT_UPVOTED -> NotificationPriority.LOW;
        };
    }

    private NotificationType resolveNotificationType(Notification notification, NotificationEvent event, NotificationPreferenceResponse preference) {
        if (notification != null && notification.getNotificationType() != null) {
            return notification.getNotificationType();
        }
        if (preference != null && preference.notificationType() != null) {
            return preference.notificationType();
        }
        if (event != null && event.type() != null) {
            try {
                return NotificationType.valueOf(event.type());
            } catch (Exception ignored) {}
        }
        return NotificationType.SYSTEM;
    }
}
