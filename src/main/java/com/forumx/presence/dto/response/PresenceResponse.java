package com.forumx.presence.dto.response;

import java.time.Instant;

public record PresenceResponse(
        Long userId,
        String username,
        Long tenantId,
        boolean online,
        String status,
        int activeSessions,
        Instant connectedAt,
        Instant lastSeen
) {}
