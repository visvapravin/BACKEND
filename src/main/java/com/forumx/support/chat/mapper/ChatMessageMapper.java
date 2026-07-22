package com.forumx.support.chat.mapper;

import com.forumx.support.chat.dto.response.ChatMessageResponse;
import com.forumx.support.chat.dto.response.ChatSessionResponse;
import com.forumx.support.chat.entity.ChatMessage;
import com.forumx.support.chat.entity.ChatSession;
import org.springframework.stereotype.Component;

@Component
public class ChatMessageMapper {

    public ChatMessageResponse toResponse(ChatMessage message) {
        if (message == null) {
            return null;
        }
        return ChatMessageResponse.builder()
                .id(message.getId())
                .sessionId(message.getSession().getId())
                .senderId(message.getSender().getId())
                .senderUsername(message.getSender().getUsername())
                .messageType(message.getMessageType().name())
                .content(message.getContent())
                .deliveryStatus(message.getDeliveryStatus().name())
                .deleted(message.isDeleted())
                .createdAt(message.getCreatedAt())
                .updatedAt(message.getUpdatedAt())
                .build();
    }

    public ChatSessionResponse toResponse(ChatSession session) {
        if (session == null) {
            return null;
        }
        return ChatSessionResponse.builder()
                .id(session.getId())
                .ticketId(session.getTicket().getId())
                .customerId(session.getCustomer().getId())
                .moderatorId(session.getModerator().getId())
                .status(session.getStatus().name())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }
}
