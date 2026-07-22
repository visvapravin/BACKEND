package com.forumx.presence.event;

import com.forumx.presence.dto.PresenceStatus;
import java.time.Instant;

public record PresenceChangedEvent(
    Long userId,
    String username,
    Long tenantId,
    PresenceStatus status,
    int activeSessions,
    Instant lastSeen
) {
}
