package com.forumx.support.chat.event.ephemeral;

public record TypingStoppedEvent(
        Long sessionId,
        Long tenantId,
        Long userId,
        String username,
        String displayName
) {
    public TypingStoppedEvent(Long sessionId, Long tenantId, Long userId, String username) {
        this(sessionId, tenantId, userId, username, username);
    }
}

