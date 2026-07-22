package com.forumx.notification.controller;

import com.forumx.notification.dto.response.NotificationResponse;
import com.forumx.notification.dto.response.UnreadCountResponse;
import com.forumx.notification.service.NotificationApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API for the authenticated recipient's notifications. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "Operations for retrieving, reading, and managing recipient notifications.")
public class NotificationController {

    private final NotificationApplicationService notificationApplicationService;

    @GetMapping
    @Operation(
            summary = "Get notifications",
            description = "Retrieves a paginated list of the authenticated user's notifications sorted by creation date descending.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Paginated notifications retrieved successfully",
                            content = @Content(schema = @Schema(implementation = Page.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid")
            }
    )
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            @Parameter(description = "Pagination parameters (default page 0, size 20, sort createdAt DESC)")
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(notificationApplicationService.getNotifications(pageable));
    }

    @GetMapping("/unread")
    @Operation(
            summary = "Get unread notifications",
            description = "Retrieves a paginated list of unread notifications for the authenticated user.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Unread notifications retrieved successfully"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized")
            }
    )
    public ResponseEntity<Page<NotificationResponse>> getUnreadNotifications(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(notificationApplicationService.getUnreadNotifications(pageable));
    }

    @GetMapping("/unread/count")
    @Operation(
            summary = "Get unread count",
            description = "Retrieves total unread notification count for the authenticated recipient.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Unread count returned",
                            content = @Content(schema = @Schema(implementation = UnreadCountResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized")
            }
    )
    public ResponseEntity<UnreadCountResponse> getUnreadCount() {
        return ResponseEntity.ok(new UnreadCountResponse(notificationApplicationService.getUnreadCount()));
    }

    @PatchMapping("/{id}/read")
    @Operation(
            summary = "Mark single notification read",
            description = "Marks a specific notification as read for the authenticated recipient.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Notification marked as read"),
                    @ApiResponse(responseCode = "404", description = "Notification not found or access denied")
            }
    )
    public ResponseEntity<NotificationResponse> markAsReadPatch(@PathVariable Long id) {
        return ResponseEntity.ok(notificationApplicationService.markAsRead(id));
    }

    @PutMapping("/{notificationId}/read")
    @Operation(summary = "Mark notification read (Legacy PUT)", description = "Maintains compatibility for PUT mark-as-read.")
    public ResponseEntity<NotificationResponse> markAsReadPut(@PathVariable Long notificationId) {
        return ResponseEntity.ok(notificationApplicationService.markAsRead(notificationId));
    }

    @PatchMapping("/read-all")
    @Operation(
            summary = "Mark all notifications read",
            description = "Marks all unread notifications for the authenticated recipient as read.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "All notifications marked read successfully")
            }
    )
    public ResponseEntity<Void> markAllAsReadPatch() {
        notificationApplicationService.markAllAsRead();
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/read-all")
    @Operation(summary = "Mark all read (Legacy PUT)", description = "Maintains compatibility for PUT mark-all-as-read.")
    public ResponseEntity<Void> markAllAsReadPut() {
        notificationApplicationService.markAllAsRead();
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete notification",
            description = "Soft-deletes a notification belonging to the authenticated recipient.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Notification deleted successfully"),
                    @ApiResponse(responseCode = "404", description = "Notification not found or access denied")
            }
    )
    public ResponseEntity<Void> deleteNotification(@PathVariable Long id) {
        notificationApplicationService.deleteNotification(id);
        return ResponseEntity.noContent().build();
    }
}
