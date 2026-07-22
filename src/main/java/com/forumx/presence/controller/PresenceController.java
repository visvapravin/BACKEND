package com.forumx.presence.controller;

import java.util.List;
import java.util.Map;

import com.forumx.presence.dto.UserPresence;
import com.forumx.presence.dto.response.OnlineUsersResponse;
import com.forumx.presence.dto.response.PresenceResponse;
import com.forumx.presence.dto.response.PresenceSummary;
import com.forumx.presence.service.PresenceService;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.tenant.resolver.TenantResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final AuthenticationFacade authenticationFacade;
    private final TenantResolver tenantResolver;

    @GetMapping("/me")
    @Operation(
            summary = "Get current user presence",
            description = "Retrieves real-time online status, session count, and last seen for the authenticated user.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Presence info retrieved successfully",
                            content = @Content(schema = @Schema(implementation = PresenceResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized")
            }
    )
    public ResponseEntity<PresenceResponse> getMyPresence() {
        CustomUserDetails userDetails = getAuthenticatedUser();
        UserPresence presence = presenceService.getPresence(userDetails.getUserId())
                .orElseGet(() -> UserPresence.builder()
                        .userId(userDetails.getUserId())
                        .username(userDetails.getUsername())
                        .tenantId(userDetails.getTenantId())
                        .status(com.forumx.presence.dto.PresenceStatus.OFFLINE)
                        .activeSessions(0)
                        .build());
        return ResponseEntity.ok(toPresenceResponse(presence));
    }

    @GetMapping("/{userId}")
    @Operation(
            summary = "Get user presence by ID",
            description = "Retrieves presence status and last seen timestamp for a specific user.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "User presence retrieved"),
                    @ApiResponse(responseCode = "404", description = "User presence not found")
            }
    )
    public ResponseEntity<PresenceResponse> getUserPresence(@PathVariable Long userId) {
        UserPresence presence = presenceService.getPresence(userId)
                .orElseThrow(() -> new EntityNotFoundException("Presence information not found for user ID: " + userId));
        return ResponseEntity.ok(toPresenceResponse(presence));
    }

    @GetMapping("/online")
    @Operation(
            summary = "Get online users for tenant",
            description = "Retrieves list of all currently online users under the active tenant.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of online users returned",
                            content = @Content(schema = @Schema(implementation = OnlineUsersResponse.class)))
            }
    )
    public ResponseEntity<OnlineUsersResponse> getOnlineUsers() {
        Long tenantId = tenantResolver.resolveTenantId();
        if (tenantId == null) {
            throw new AccessDeniedException("No tenant context available");
        }
        List<UserPresence> onlineList = presenceService.getTenantOnlineUsers(tenantId);
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
        return getOnlineUsers();
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

    private CustomUserDetails getAuthenticatedUser() {
        CustomUserDetails userDetails = authenticationFacade.getCurrentUserDetails();
        if (userDetails == null) {
            throw new AccessDeniedException("User is not authenticated");
        }
        return userDetails;
    }

    private PresenceResponse toPresenceResponse(UserPresence p) {
        return new PresenceResponse(
                p.getUserId(),
                p.getUsername(),
                p.getTenantId(),
                p.getStatus() == com.forumx.presence.dto.PresenceStatus.ONLINE,
                p.getStatus() != null ? p.getStatus().name() : "OFFLINE",
                p.getActiveSessions(),
                null,
                p.getLastSeen()
        );
    }
}
