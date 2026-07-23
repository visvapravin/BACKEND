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
public class ParticipantResponse {
    private Long id;
    private Long ticketId;
    private Long sessionId;
    private Long userId;
    private String username;
    private String role;
    private Instant joinedAt;
    private Instant leftAt;
    private boolean isActive;
    private boolean isOnline;
}
