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
public class SessionPresenceInfo {
    private String sessionId;
    private Long userId;
    private String username;
    private Long tenantId;
    private Instant connectedAt;
    private Instant lastHeartbeat;
}
