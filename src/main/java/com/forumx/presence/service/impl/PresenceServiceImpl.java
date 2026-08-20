package com.forumx.presence.service.impl;

import com.forumx.presence.config.PresenceProperties;
import com.forumx.presence.dto.PresenceStatus;
import com.forumx.presence.dto.UserPresence;
import com.forumx.presence.event.PresenceChangedEvent;
import com.forumx.presence.service.PresenceService;
import com.forumx.redis.constant.RedisKeys;
import com.forumx.redis.gateway.RedisGateway;
import com.forumx.redis.pubsub.RedisEvent;
import com.forumx.redis.pubsub.RedisPublisher;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceServiceImpl implements PresenceService {

    public static final String PRESENCE_CHANNEL = "forumx:channel:presence";

    private final RedisGateway redisGateway;
    private final PresenceProperties properties;

    @Autowired(required = false)
    private RedisPublisher redisPublisher;

    @Override
    public void markOnline(Long userId, String username, Long tenantId, String sessionId) {
        if (!properties.isEnabled() || userId == null || sessionId == null) {
            return;
        }

        // 1. Store individual session info mapping
        String sessionKey = RedisKeys.presenceSession(sessionId);
        com.forumx.presence.dto.SessionPresenceInfo sessionInfo = com.forumx.presence.dto.SessionPresenceInfo.builder()
                .sessionId(sessionId)
                .userId(userId)
                .username(username)
                .tenantId(tenantId)
                .connectedAt(Instant.now())
                .lastHeartbeat(Instant.now())
                .build();
        redisGateway.put(sessionKey, sessionInfo, properties.getTtl());

        // 2. Add session to global user session set (kept for backward-compat — isOnline / notification engine)
        String sessionsKey = RedisKeys.presenceSessions(userId);
        redisGateway.sAdd(sessionsKey, sessionId);
        redisGateway.expire(sessionsKey, properties.getTtl());

        // 2b. Add session to tenant-scoped user session set (authoritative for isUserOnlineInTenant)
        if (tenantId != null) {
            String tenantSessionsKey = RedisKeys.presenceTenantUserSessions(tenantId, userId);
            redisGateway.sAdd(tenantSessionsKey, sessionId);
            redisGateway.expire(tenantSessionsKey, properties.getTtl());
        }

        long activeSessions = tenantId != null
                ? redisGateway.sCard(RedisKeys.presenceTenantUserSessions(tenantId, userId))
                : redisGateway.sCard(sessionsKey);

        // 3. Add to tenant online set if tenantId exists
        if (tenantId != null) {
            String tenantOnlineKey = RedisKeys.presenceTenantOnline(tenantId);
            redisGateway.sAdd(tenantOnlineKey, String.valueOf(userId));
        }

        // 4. Update user presence data
        UserPresence presence = UserPresence.builder()
                .userId(userId)
                .username(username)
                .tenantId(tenantId)
                .status(PresenceStatus.ONLINE)
                .activeSessions((int) activeSessions)
                .lastSeen(Instant.now())
                .build();

        String userKey = RedisKeys.presence(userId);
        redisGateway.put(userKey, presence, properties.getTtl());

        log.info("User marked online. userId={}, username={}, tenantId={}, sessionId={}, activeSessions={}",
                userId, username, tenantId, sessionId, activeSessions);

        if (properties.isBroadcastEnabled()) {
            broadcastPresenceChange(presence);
        }
    }

    @Override
    public void markOfflineBySessionId(String sessionId) {
        if (!properties.isEnabled() || sessionId == null) {
            return;
        }
        String sessionKey = RedisKeys.presenceSession(sessionId);
        Optional<com.forumx.presence.dto.SessionPresenceInfo> sessionOpt = redisGateway.get(sessionKey, com.forumx.presence.dto.SessionPresenceInfo.class);
        if (sessionOpt.isPresent()) {
            markOffline(sessionOpt.get().getUserId(), sessionId);
        } else {
            log.debug("SessionInfo not found for sessionId={}", sessionId);
        }
    }

    @Override
    public void updateHeartbeat(String sessionId) {
        if (!properties.isEnabled() || sessionId == null) {
            return;
        }
        String sessionKey = RedisKeys.presenceSession(sessionId);
        Optional<com.forumx.presence.dto.SessionPresenceInfo> sessionOpt = redisGateway.get(sessionKey, com.forumx.presence.dto.SessionPresenceInfo.class);
        if (sessionOpt.isPresent()) {
            com.forumx.presence.dto.SessionPresenceInfo info = sessionOpt.get();
            info.setLastHeartbeat(Instant.now());
            redisGateway.put(sessionKey, info, properties.getTtl());
            // Refresh global keys (notification engine)
            redisGateway.expire(RedisKeys.presenceSessions(info.getUserId()), properties.getTtl());
            redisGateway.expire(RedisKeys.presence(info.getUserId()), properties.getTtl());
            // Refresh tenant-scoped session set TTL
            if (info.getTenantId() != null) {
                redisGateway.expire(
                        RedisKeys.presenceTenantUserSessions(info.getTenantId(), info.getUserId()),
                        properties.getTtl());
            }
        }
    }

    @Override
    public void markOffline(Long userId, String sessionId) {
        if (!properties.isEnabled() || userId == null) {
            return;
        }

        // Read session info BEFORE deleting — it contains the authoritative tenantId for this session
        Long sessionTenantId = null;
        if (sessionId != null) {
            String sessionKey = RedisKeys.presenceSession(sessionId);
            Optional<com.forumx.presence.dto.SessionPresenceInfo> sessionInfoOpt =
                    redisGateway.get(sessionKey, com.forumx.presence.dto.SessionPresenceInfo.class);
            if (sessionInfoOpt.isPresent()) {
                sessionTenantId = sessionInfoOpt.get().getTenantId();
            }
            redisGateway.delete(sessionKey);

            // Remove from global session set
            redisGateway.sRem(RedisKeys.presenceSessions(userId), sessionId);

            // Remove from tenant-scoped session set (using tenantId from the session info)
            if (sessionTenantId != null) {
                redisGateway.sRem(RedisKeys.presenceTenantUserSessions(sessionTenantId, userId), sessionId);
            }
        }

        // Determine remaining active sessions in the tenant
        long tenantActiveSessions = sessionTenantId != null
                ? redisGateway.sCard(RedisKeys.presenceTenantUserSessions(sessionTenantId, userId))
                : 0;
        // Also check global sessions for the UserPresence update
        long globalActiveSessions = redisGateway.sCard(RedisKeys.presenceSessions(userId));

        String userKey = RedisKeys.presence(userId);
        Optional<UserPresence> existingOpt = redisGateway.get(userKey, UserPresence.class);

        if (existingOpt.isPresent()) {
            UserPresence presence = existingOpt.get();
            presence.setLastSeen(Instant.now());
            presence.setActiveSessions((int) tenantActiveSessions);

            if (tenantActiveSessions == 0 && sessionTenantId != null) {
                // No remaining sessions for this user in this specific tenant → remove from tenant online set
                redisGateway.sRem(RedisKeys.presenceTenantOnline(sessionTenantId), String.valueOf(userId));
                log.info("User removed from tenant online set. userId={}, tenantId={}, sessionId={}",
                        userId, sessionTenantId, sessionId);
            }

            if (globalActiveSessions == 0) {
                // No sessions at all — fully offline
                presence.setStatus(PresenceStatus.OFFLINE);
                redisGateway.put(userKey, presence, properties.getTtl());
                redisGateway.delete(RedisKeys.presenceSessions(userId));
                log.info("User marked offline (all sessions closed). userId={}, username={}, sessionId={}",
                        userId, presence.getUsername(), sessionId);
            } else {
                presence.setStatus(PresenceStatus.ONLINE);
                redisGateway.put(userKey, presence, properties.getTtl());
                redisGateway.expire(RedisKeys.presenceSessions(userId), properties.getTtl());
                log.info("User session closed. userId={}, username={}, sessionId={}, globalRemaining={}, tenantRemaining={}",
                        userId, presence.getUsername(), sessionId, globalActiveSessions, tenantActiveSessions);
            }

            if (properties.isBroadcastEnabled()) {
                broadcastPresenceChange(presence);
            }
        }
    }

    @Override
    public boolean isOnline(Long userId) {
        if (!properties.isEnabled()) {
            return false;
        }
        return getActiveSessions(userId) > 0;
    }

    @Override
    public boolean isUserOnlineInTenant(Long tenantId, Long userId) {
        if (!properties.isEnabled() || tenantId == null || userId == null) {
            return false;
        }
        return redisGateway.sCard(RedisKeys.presenceTenantUserSessions(tenantId, userId)) > 0;
    }

    @Override
    public Optional<UserPresence> getPresence(Long userId) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        Optional<UserPresence> presenceOpt = redisGateway.get(RedisKeys.presence(userId), UserPresence.class);
        if (presenceOpt.isPresent()) {
            UserPresence presence = presenceOpt.get();
            long activeSessions = redisGateway.sCard(RedisKeys.presenceSessions(userId));
            presence.setActiveSessions((int) activeSessions);
            if (activeSessions == 0) {
                presence.setStatus(PresenceStatus.OFFLINE);
            } else {
                presence.setStatus(PresenceStatus.ONLINE);
            }
            return Optional.of(presence);
        }
        return Optional.empty();
    }

    @Override
    public Map<Long, UserPresence> getPresence(Collection<Long> userIds) {
        Map<Long, UserPresence> result = new HashMap<>();
        if (!properties.isEnabled() || userIds == null) {
            return result;
        }
        for (Long userId : userIds) {
            getPresence(userId).ifPresent(presence -> result.put(userId, presence));
        }
        return result;
    }

    @Override
    public int getActiveSessions(Long userId) {
        if (!properties.isEnabled() || userId == null) {
            return 0;
        }
        return (int) redisGateway.sCard(RedisKeys.presenceSessions(userId));
    }

    @Override
    public java.util.List<UserPresence> getTenantOnlineUsers(Long tenantId) {
        if (!properties.isEnabled() || tenantId == null) {
            return java.util.Collections.emptyList();
        }
        String tenantOnlineKey = RedisKeys.presenceTenantOnline(tenantId);
        java.util.Set<String> onlineUserIds = redisGateway.sMembers(tenantOnlineKey);
        if (onlineUserIds == null || onlineUserIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        java.util.List<UserPresence> result = new java.util.ArrayList<>();
        for (String idStr : onlineUserIds) {
            try {
                Long uid = Long.parseLong(idStr);
                getPresence(uid).ifPresent(result::add);
            } catch (Exception ignored) {}
        }
        return result;
    }

    @Override
    public int getTenantOnlineCount(Long tenantId) {
        if (!properties.isEnabled() || tenantId == null) {
            return 0;
        }
        return (int) redisGateway.sCard(RedisKeys.presenceTenantOnline(tenantId));
    }

    @Override
    public void evictPresence(Long userId, Long tenantId) {
        if (!properties.isEnabled() || userId == null) {
            return;
        }
        String sessionsKey = RedisKeys.presenceSessions(userId);
        java.util.Set<String> sessions = redisGateway.sMembers(sessionsKey);
        if (sessions != null && !sessions.isEmpty()) {
            for (String sessionId : sessions) {
                redisGateway.delete(RedisKeys.presenceSession(sessionId));
            }
        }
        redisGateway.delete(sessionsKey);
        redisGateway.delete(RedisKeys.presence(userId));
        if (tenantId != null) {
            redisGateway.sRem(RedisKeys.presenceTenantOnline(tenantId), String.valueOf(userId));
            // Also clean up tenant-scoped session set
            redisGateway.delete(RedisKeys.presenceTenantUserSessions(tenantId, userId));
        }
        log.info("Evicted presence for user. userId={}, tenantId={}", userId, tenantId);
    }


    private void broadcastPresenceChange(UserPresence presence) {
        if (redisPublisher == null) {
            log.debug("RedisPublisher not available. Skipping presence change broadcast.");
            return;
        }

        PresenceChangedEvent event = new PresenceChangedEvent(
                presence.getUserId(),
                presence.getUsername(),
                presence.getTenantId(),
                presence.getStatus(),
                presence.getActiveSessions(),
                presence.getLastSeen()
        );

        try {
            log.info("Broadcasting presence update. userId={}, status={}, activeSessions={}",
                    presence.getUserId(), presence.getStatus(), presence.getActiveSessions());
            redisPublisher.publish(PRESENCE_CHANNEL, RedisEvent.of("PRESENCE_CHANGED", event));
        } catch (Exception e) {
            log.error("Failed to publish presence changed event to Redis Pub/Sub", e);
        }
    }
}
