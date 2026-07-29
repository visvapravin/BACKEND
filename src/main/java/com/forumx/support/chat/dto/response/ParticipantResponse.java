package com.forumx.support.chat.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty("isActive")
    private boolean isActive;

    @JsonProperty("isOnline")
    private boolean isOnline;
}
