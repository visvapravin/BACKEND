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
public class ChatSessionResponse {
    private Long id;
    private Long ticketId;
    private Long customerId;
    private Long moderatorId;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}
