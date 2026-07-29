package com.forumx.support.chat.event.ephemeral;

public record TypingStartedEvent(
        Long sessionId,
        Long tenantId,
        Long userId,
        String username,
        String displayName
) {
    public TypingStartedEvent(Long sessionId, Long tenantId, Long userId, String username) {
        this(sessionId, tenantId, userId, username, username);
    }
}

