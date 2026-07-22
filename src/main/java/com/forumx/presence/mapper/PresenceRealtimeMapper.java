package com.forumx.presence.mapper;

import com.forumx.presence.dto.UserPresence;
import com.forumx.presence.event.PresenceChangedEvent;
import com.forumx.websocket.dto.RealtimeEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class PresenceRealtimeMapper {

    public RealtimeEvent<UserPresence> toRealtimeEvent(PresenceChangedEvent event) {
        UserPresence payload = UserPresence.builder()
                .userId(event.userId())
                .username(event.username())
                .tenantId(event.tenantId())
                .status(event.status())
                .activeSessions(event.activeSessions())
                .lastSeen(event.lastSeen())
                .build();

        return RealtimeEvent.<UserPresence>builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .type("PRESENCE_CHANGED")
                .payload(payload)
                .build();
    }
}
