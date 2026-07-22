package com.forumx.presence.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forumx.presence.dto.UserPresence;
import com.forumx.presence.event.PresenceChangedEvent;
import com.forumx.presence.mapper.PresenceRealtimeMapper;
import com.forumx.presence.service.impl.PresenceServiceImpl;
import com.forumx.redis.pubsub.RedisEvent;
import com.forumx.redis.pubsub.RedisSubscriber;
import com.forumx.redis.serializer.RedisObjectSerializer;
import com.forumx.websocket.dto.RealtimeEvent;
import com.forumx.websocket.gateway.RealtimeGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "forumx.redis.enabled", havingValue = "true")
public class PresenceEventSubscriber implements RedisSubscriber {

    private final PresenceRealtimeMapper realtimeMapper;
    private final RealtimeGateway realtimeGateway;
    private final ObjectMapper objectMapper = RedisObjectSerializer.createObjectMapper();

    @Override
    public String getChannel() {
        return PresenceServiceImpl.PRESENCE_CHANNEL;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String bodyJson = new String(message.getBody(), StandardCharsets.UTF_8);
            log.debug("Received Redis presence event: {}", bodyJson);

            RedisEvent<?> rawEvent = objectMapper.readValue(bodyJson, RedisEvent.class);
            PresenceChangedEvent event;
            if (rawEvent.payload() instanceof PresenceChangedEvent) {
                event = (PresenceChangedEvent) rawEvent.payload();
            } else {
                event = objectMapper.convertValue(rawEvent.payload(), PresenceChangedEvent.class);
            }

            log.info("Processing presence changed event from Redis. userId={}, status={}, activeSessions={}",
                    event.userId(), event.status(), event.activeSessions());

            RealtimeEvent<UserPresence> realtimeEvent = realtimeMapper.toRealtimeEvent(event);

            realtimeGateway.sendToTopic("/topic/presence", realtimeEvent);

            log.info("Successfully broadcast presence update to WebSocket topic. userId={}, status={}",
                    event.userId(), event.status());
        } catch (Exception e) {
            log.error("Failed to process presence update from Redis channel", e);
        }
    }
}
