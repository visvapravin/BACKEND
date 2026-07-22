package com.forumx.notification.delivery.strategy.impl;

import com.forumx.notification.delivery.domain.DeliveryExecutionResult;
import com.forumx.notification.delivery.domain.NotificationDeliveryContext;
import com.forumx.notification.delivery.entity.DeliveryChannel;
import com.forumx.notification.delivery.strategy.DeliveryStrategy;
import com.forumx.notification.mapper.NotificationMapper;
import com.forumx.websocket.dto.WebSocketNotificationDto;
import com.forumx.websocket.service.WebSocketNotificationService;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketDeliveryStrategy implements DeliveryStrategy {

    private final WebSocketNotificationService webSocketNotificationService;
    private final NotificationMapper notificationMapper;

    @Override
    public DeliveryChannel getChannel() {
        return DeliveryChannel.WEBSOCKET;
    }

    @Override
    public DeliveryExecutionResult deliver(NotificationDeliveryContext ctx) {
        Instant start = Instant.now();
        if (ctx.notification() == null || ctx.notification().getRecipient() == null) {
            return DeliveryExecutionResult.failure("Missing notification entity or recipient user", Duration.between(start, Instant.now()));
        }

        String recipientUsername = ctx.notification().getRecipient().getUsername();
        try {
            log.info("[WebSocketStrategy] Executing WebSocket delivery to username={}, correlationId={}", recipientUsername, ctx.correlationId());
            WebSocketNotificationDto dto = notificationMapper.toWebSocketDto(ctx.notification());
            webSocketNotificationService.sendNotification(recipientUsername, dto);
            return DeliveryExecutionResult.success("STOMP Delivered", Duration.between(start, Instant.now()));
        } catch (Exception e) {
            log.error("[WebSocketStrategy] Error delivering WebSocket to username={}: {}", recipientUsername, e.getMessage());
            return DeliveryExecutionResult.failure(e.getMessage(), Duration.between(start, Instant.now()));
        }
    }
}
