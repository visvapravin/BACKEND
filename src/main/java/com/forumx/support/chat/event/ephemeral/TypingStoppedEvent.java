package com.forumx.support.chat.event.ephemeral;

public record TypingStoppedEvent(
        Long sessionId,
        Long tenantId,
        Long userId,
        String username
) {
}
