package com.forumx.presence.service;

import com.forumx.presence.dto.UserPresence;
import java.util.Optional;

public interface PresenceService {
    void markOnline(Long userId, String username, Long tenantId, String sessionId);
    void markOffline(Long userId, String sessionId);
    void markOfflineBySessionId(String sessionId);
    void updateHeartbeat(String sessionId);
    boolean isOnline(Long userId);
    /**
     * Returns true only if the user has at least one active WebSocket session
     * within the specified tenant. This is the authoritative check for
     * tenant-scoped presence (e.g. dashboard online counts, participant status).
     */
    boolean isUserOnlineInTenant(Long tenantId, Long userId);
    Optional<UserPresence> getPresence(Long userId);
    java.util.Map<Long, UserPresence> getPresence(java.util.Collection<Long> userIds);
    int getActiveSessions(Long userId);
    java.util.List<UserPresence> getTenantOnlineUsers(Long tenantId);
    int getTenantOnlineCount(Long tenantId);
    void evictPresence(Long userId, Long tenantId);
}


