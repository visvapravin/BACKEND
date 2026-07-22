package com.forumx.support.chat.dto.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {
    private Long id;
    private Long sessionId;
    private Long senderId;
    private String senderUsername;
    private String messageType;
    private String content;
    private String deliveryStatus;
    private boolean deleted;
    private Instant createdAt;
    private Instant updatedAt;
}
