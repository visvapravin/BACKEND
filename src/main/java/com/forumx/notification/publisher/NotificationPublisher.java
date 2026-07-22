package com.forumx.notification.publisher;

import com.forumx.mail.EmailService;
import com.forumx.notification.dto.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPublisher {

    private final ObjectProvider<RabbitTemplate> rabbitTemplateProvider;
    private final EmailService emailService;

    public void publish(NotificationEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            log.info("Transaction active. Deferring publishing of notification for {} until after commit.", event.email());
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendEvent(event);
                }
            });
        } else {
            sendEvent(event);
        }
    }

    private void sendEvent(NotificationEvent event) {
        RabbitTemplate rabbitTemplate = rabbitTemplateProvider.getIfAvailable();
        if (rabbitTemplate != null) {
            log.info("Publishing notification for {}", event.email());
            try {
                rabbitTemplate.convertAndSend(
                        "forumx.notification.exchange",
                        "notification.email.send",
                        event
                );
            } catch (Exception e) {
                log.error("Failed to publish notification for {} to RabbitMQ, falling back to synchronous send. Error: {}", event.email(), e.getMessage());
                emailService.sendVerificationEmail(event.email(), event.subject(), event.body());
            }
        } else {
            log.info("RabbitMQ is disabled. Sending notification for {} synchronously.", event.email());
            emailService.sendVerificationEmail(event.email(), event.subject(), event.body());
        }
    }
}
