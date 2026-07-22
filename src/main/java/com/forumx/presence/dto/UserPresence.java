package com.forumx.presence.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPresence {
    private Long userId;
    private String username;
    private Long tenantId;
    private PresenceStatus status;
    private int activeSessions;
    private Instant lastSeen;
}
