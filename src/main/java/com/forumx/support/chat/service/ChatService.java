package com.forumx.support.chat.service;

import com.forumx.support.chat.dto.request.SendMessageRequest;
import com.forumx.support.chat.entity.ChatMessage;
import com.forumx.support.chat.entity.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface ChatService {
    ChatMessage sendMessage(Long ticketId, SendMessageRequest request);
    void deleteMessage(Long messageId);
    void markRead(Long ticketId);
    Page<ChatMessage> getMessages(Long ticketId, Long beforeMessageId, Pageable pageable);
    List<ChatMessage> getLatestMessages(Long ticketId, Long beforeMessageId, int size);
    ChatSession getSession(Long ticketId);
}
