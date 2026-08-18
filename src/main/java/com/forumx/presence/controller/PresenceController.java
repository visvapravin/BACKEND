package com.forumx.presence.controller;

import java.util.List;
import java.util.Map;

import com.forumx.presence.dto.UserPresence;
import com.forumx.presence.dto.response.OnlineUsersResponse;
import com.forumx.presence.dto.response.PresenceSummary;
import com.forumx.presence.service.PresenceService;
import com.forumx.tenant.resolver.TenantResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API controller for Redis Presence and User Session Management. */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/presence")
@Tag(name = "Presence", description = "Operations for retrieving real-time online status and active sessions.")
public class PresenceController {

    private final PresenceService presenceService;
    private final TenantResolver tenantResolver;
    private final com.forumx.auth.repository.UserRepository userRepository;

    @GetMapping("/online")
    @Operation(
            summary = "Get online users for tenant",
            description = "Retrieves list of all currently online users under the active tenant with optional role filtering.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of online users returned",
                            content = @Content(schema = @Schema(implementation = OnlineUsersResponse.class)))
            }
    )
    public ResponseEntity<OnlineUsersResponse> getOnlineUsers(
            @org.springframework.web.bind.annotation.RequestParam(required = false) com.forumx.auth.enums.RoleType role) {
        Long tenantId = tenantResolver.resolveTenantId();
        if (tenantId == null) {
            throw new AccessDeniedException("No tenant context available");
        }
        List<UserPresence> onlineList = presenceService.getTenantOnlineUsers(tenantId);
        if (role != null) {
            List<com.forumx.auth.enums.RoleType> targetRoles = (role == com.forumx.auth.enums.RoleType.MODERATOR)
                    ? List.of(com.forumx.auth.enums.RoleType.MODERATOR, com.forumx.auth.enums.RoleType.TENANT_ADMIN, com.forumx.auth.enums.RoleType.PLATFORM_ADMIN)
                    : List.of(role);
            java.util.Set<Long> allowedUserIds = userRepository.findUsersByTenantIdAndRoles(tenantId, targetRoles)
                    .stream().map(com.forumx.auth.entity.User::getId).collect(java.util.stream.Collectors.toSet());
            onlineList = onlineList.stream().filter(p -> allowedUserIds.contains(p.getUserId())).toList();
        }
        List<PresenceSummary> summaries = onlineList.stream()
                .map(p -> new PresenceSummary(
                        p.getUserId(),
                        p.getUsername(),
                        p.getStatus() == com.forumx.presence.dto.PresenceStatus.ONLINE,
                        p.getStatus() != null ? p.getStatus().name() : "OFFLINE",
                        p.getLastSeen()
                ))
                .toList();

        return ResponseEntity.ok(new OnlineUsersResponse(summaries, summaries.size()));
    }

    @GetMapping("/tenant/online")
    @Operation(summary = "Get online users for tenant (Alias)", description = "Alias endpoint for tenant online listing.")
    public ResponseEntity<OnlineUsersResponse> getTenantOnlineUsers() {
        return getOnlineUsers(null);
    }

    @GetMapping("/count")
    @Operation(
            summary = "Get tenant online count",
            description = "Returns the total number of currently online users under the active tenant."
    )
    public ResponseEntity<Map<String, Object>> getOnlineCount() {
        Long tenantId = tenantResolver.resolveTenantId();
        if (tenantId == null) {
            throw new AccessDeniedException("No tenant context available");
        }
        int count = presenceService.getTenantOnlineCount(tenantId);
        return ResponseEntity.ok(Map.of("tenantId", tenantId, "onlineCount", count));
    }
}
