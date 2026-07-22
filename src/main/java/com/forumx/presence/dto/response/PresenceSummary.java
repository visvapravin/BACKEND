package com.forumx.presence.dto.response;

import java.time.Instant;

public record PresenceSummary(
        Long userId,
        String username,
        boolean online,
        String status,
        Instant lastSeen
) {}
