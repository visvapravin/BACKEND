package com.forumx.notification.delivery.strategy.impl;

import com.forumx.notification.delivery.domain.DeliveryExecutionResult;
import com.forumx.notification.delivery.domain.NotificationDeliveryContext;
import com.forumx.notification.delivery.entity.DeliveryChannel;
import com.forumx.notification.delivery.exception.PermanentDeliveryException;
import com.forumx.notification.delivery.exception.RetryableDeliveryException;
import com.forumx.notification.delivery.strategy.DeliveryStrategy;
import com.forumx.notification.email.service.EmailService;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailDeliveryStrategy implements DeliveryStrategy {

    private final EmailService notificationEmailService;

    @Override
    public DeliveryChannel getChannel() {
        return DeliveryChannel.EMAIL;
    }

    @Override
    @SuppressWarnings("unchecked")
    public DeliveryExecutionResult deliver(NotificationDeliveryContext context) {
        Instant start = Instant.now();
        try {
            if (context.recipient() == null || context.recipient().getEmail() == null) {
                throw new PermanentDeliveryException("Recipient or email address is missing");
            }

            Map<String, String> model = Map.of();
            if (context.metadata() != null && context.metadata().containsKey("model")) {
                Object modelObj = context.metadata().get("model");
                if (modelObj instanceof Map) {
                    model = (Map<String, String>) modelObj;
                }
            }

            notificationEmailService.processAndSendEmail(
                    context.tenant() != null ? context.tenant().getId() : null,
                    context.recipient().getEmail(),
                    context.recipient().getUsername(),
                    /* Email template resolution */
                    com.forumx.notification.email.EmailTemplateType.GENERIC,
                    model
            );

            return DeliveryExecutionResult.success("SMTP Dispatch OK", Duration.between(start, Instant.now()));
        } catch (PermanentDeliveryException e) {
            throw e;
        } catch (Exception e) {
            throw new RetryableDeliveryException("SMTP Delivery failed: " + e.getMessage(), e);
        }
    }
}
