package com.forumx.support.chat.event.ephemeral;

import java.time.Instant;

public record ChatParticipantLeftEvent(
        Long ticketId,
        Long sessionId,
        Long tenantId,
        Long userId,
        String username,
        String role,
        Instant leftAt
) {
}
