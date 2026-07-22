package com.forumx.support.queue.publisher;

import com.forumx.messaging.constant.MessagingExchanges;
import com.forumx.messaging.constant.MessagingRoutingKeys;
import com.forumx.messaging.dto.EventEnvelope;
import com.forumx.messaging.gateway.EventGateway;
import com.forumx.support.queue.event.SupportTicketClaimedEvent;
import com.forumx.support.queue.event.SupportTicketQueuedEvent;
import com.forumx.support.queue.event.SupportTicketStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "forumx.messaging.enabled", havingValue = "true")
public class SupportQueueEventPublisher {

    private final EventGateway eventGateway;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTicketQueued(SupportTicketQueuedEvent event) {
        log.info("Handling transactional event for support ticket queued. ticketId={}, tenantId={}, priority={}",
                event.ticketId(), event.tenantId(), event.priority());

        try {
            EventEnvelope<SupportTicketQueuedEvent> envelope = EventEnvelope.of(
                    "SUPPORT_TICKET_QUEUED",
                    event.tenantId(),
                    "support-queue-service",
                    event
            );

            eventGateway.publish(
                    MessagingExchanges.SUPPORT_EVENTS_EXCHANGE,
                    MessagingRoutingKeys.SUPPORT_QUEUED,
                    envelope
            );

            log.info("Successfully published SupportTicketQueuedEvent to RabbitMQ. eventId={}, ticketId={}",
                    envelope.eventId(), event.ticketId());
        } catch (Exception e) {
            log.error("Failed to publish SupportTicketQueuedEvent to RabbitMQ. ticketId={}, error={}",
                    event.ticketId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTicketClaimed(SupportTicketClaimedEvent event) {
        log.info("Handling transactional event for support ticket claimed. ticketId={}, tenantId={}, moderatorId={}",
                event.ticketId(), event.tenantId(), event.moderatorId());

        try {
            EventEnvelope<SupportTicketClaimedEvent> envelope = EventEnvelope.of(
                    "SUPPORT_TICKET_CLAIMED",
                    event.tenantId(),
                    "support-queue-service",
                    event
            );

            eventGateway.publish(
                    MessagingExchanges.SUPPORT_EVENTS_EXCHANGE,
                    MessagingRoutingKeys.SUPPORT_CLAIMED,
                    envelope
            );

            log.info("Successfully published SupportTicketClaimedEvent to RabbitMQ. eventId={}, ticketId={}",
                    envelope.eventId(), event.ticketId());
        } catch (Exception e) {
            log.error("Failed to publish SupportTicketClaimedEvent to RabbitMQ. ticketId={}, error={}",
                    event.ticketId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTicketStatusChanged(SupportTicketStatusChangedEvent event) {
        log.info("Handling transactional event for support ticket status change. ticketId={}, tenantId={}, newStatus={}",
                event.ticketId(), event.tenantId(), event.newStatus());

        try {
            EventEnvelope<SupportTicketStatusChangedEvent> envelope = EventEnvelope.of(
                    "SUPPORT_TICKET_STATUS_CHANGED",
                    event.tenantId(),
                    "support-queue-service",
                    event
            );

            eventGateway.publish(
                    MessagingExchanges.SUPPORT_EVENTS_EXCHANGE,
                    MessagingRoutingKeys.SUPPORT_STATUS_CHANGED,
                    envelope
            );

            log.info("Successfully published SupportTicketStatusChangedEvent to RabbitMQ. eventId={}, ticketId={}",
                    envelope.eventId(), event.ticketId());
        } catch (Exception e) {
            log.error("Failed to publish SupportTicketStatusChangedEvent to RabbitMQ. ticketId={}, error={}",
                    event.ticketId(), e.getMessage(), e);
        }
    }
}
