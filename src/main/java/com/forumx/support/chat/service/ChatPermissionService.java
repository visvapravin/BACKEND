package com.forumx.support.chat.service;

import com.forumx.support.chat.entity.ChatMessage;
import com.forumx.support.chat.entity.ChatSession;

public interface ChatPermissionService {
    void assertCanJoin(ChatSession session, Long userId);
    void assertCanSend(ChatSession session, Long userId);
    void assertCanRead(ChatSession session, Long userId);
    void assertCanDelete(ChatSession session, Long userId, ChatMessage message);
}
