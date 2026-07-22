package com.forumx.notification.preference.controller;

import java.util.List;

import com.forumx.notification.entity.NotificationType;
import com.forumx.notification.preference.dto.request.UpdateNotificationPreferenceRequest;
import com.forumx.notification.preference.dto.response.NotificationPreferenceResponse;
import com.forumx.notification.preference.service.NotificationPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API controller for managing user notification preferences. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/preferences/notifications")
@Tag(name = "Notification Preferences", description = "Endpoints for retrieving and configuring channel notification preferences.")
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;

    @GetMapping
    @Operation(
            summary = "Get user notification preferences",
            description = "Retrieves effective notification preferences for all supported notification types for the authenticated user.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Preferences retrieved successfully"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized")
            }
    )
    public ResponseEntity<List<NotificationPreferenceResponse>> getUserPreferences() {
        return ResponseEntity.ok(preferenceService.getUserPreferences());
    }

    @PutMapping
    @Operation(
            summary = "Update notification preference",
            description = "Updates channel preferences for a specific notification type.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Preference updated successfully",
                            content = @Content(schema = @Schema(implementation = NotificationPreferenceResponse.class)))
            }
    )
    public ResponseEntity<NotificationPreferenceResponse> updatePreference(
            @Valid @RequestBody UpdateNotificationPreferenceRequest request) {
        return ResponseEntity.ok(preferenceService.updatePreference(request));
    }

    @PatchMapping("/{type}")
    @Operation(
            summary = "Patch preference by notification type",
            description = "Patches channel preferences for a specific notification type URL parameter."
    )
    public ResponseEntity<NotificationPreferenceResponse> patchPreferenceForType(
            @PathVariable NotificationType type,
            @RequestBody UpdateNotificationPreferenceRequest request) {
        return ResponseEntity.ok(preferenceService.updatePreferenceForType(type, request));
    }

    @PostMapping("/reset")
    @Operation(
            summary = "Reset notification preferences",
            description = "Resets user preferences back to system defaults."
    )
    public ResponseEntity<Void> resetPreferences() {
        preferenceService.resetUserPreferences();
        return ResponseEntity.noContent().build();
    }
}
