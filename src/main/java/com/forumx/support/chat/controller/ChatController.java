package com.forumx.support.chat.controller;

import com.forumx.auth.entity.User;
import com.forumx.auth.repository.UserRepository;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.support.chat.dto.request.SendMessageRequest;
import com.forumx.support.chat.dto.response.ChatMessageResponse;
import com.forumx.support.chat.dto.response.ChatSessionResponse;
import com.forumx.support.chat.dto.response.ParticipantResponse;
import com.forumx.support.chat.entity.ChatMessage;
import com.forumx.support.chat.entity.ChatSession;
import com.forumx.support.chat.entity.ParticipantRole;
import com.forumx.support.chat.entity.SupportSessionParticipant;
import com.forumx.support.chat.mapper.ChatMessageMapper;
import com.forumx.support.chat.service.ChatService;
import com.forumx.support.chat.service.ChatSessionService;
import com.forumx.tenant.resolver.TenantResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat")
@Tag(name = "Support Chat", description = "Live support chat session and room operations.")
public class ChatController {

    private final ChatService chatService;
    private final ChatSessionService chatSessionService;
    private final ChatMessageMapper chatMessageMapper;
    private final AuthenticationFacade authenticationFacade;
    private final UserRepository userRepository;
    private final TenantResolver tenantResolver;

    @PostMapping("/{ticketId}/messages")
    @Operation(summary = "Send support chat message")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @PathVariable Long ticketId,
            @Valid @RequestBody SendMessageRequest request
    ) {
        try {
            ChatMessage msg = chatService.sendMessage(ticketId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(chatMessageMapper.toResponse(msg));
        } catch (IllegalStateException e) {
            if (e.getMessage().contains("closed")) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage(), e);
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @DeleteMapping("/messages/{messageId}")
    @Operation(summary = "Soft-delete support chat message")
    public ResponseEntity<Void> deleteMessage(@PathVariable Long messageId) {
        try {
            chatService.deleteMessage(messageId);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage(), e);
        }
    }

    @PostMapping("/{ticketId}/read")
    @Operation(summary = "Bulk mark chat messages as read")
    public ResponseEntity<Void> markRead(@PathVariable Long ticketId) {
        chatService.markRead(ticketId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{ticketId}/messages")
    @Operation(summary = "Get paginated chat messages for ticket")
    public ResponseEntity<Page<ChatMessageResponse>> getMessages(
            @PathVariable Long ticketId,
            @RequestParam(required = false) Long beforeMessageId,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<ChatMessage> messages = chatService.getMessages(ticketId, beforeMessageId, pageable);
        return ResponseEntity.ok(messages.map(chatMessageMapper::toResponse));
    }

    @GetMapping("/{ticketId}/session")
    @Operation(summary = "Get support chat session details")
    public ResponseEntity<ChatSessionResponse> getSession(@PathVariable Long ticketId) {
        try {
            ChatSession session = chatService.getSession(ticketId);
            return ResponseEntity.ok(chatMessageMapper.toResponse(session));
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (AccessDeniedException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage(), e);
        }
    }

    @PostMapping("/{ticketId}/join")
    @Operation(summary = "Join support room as moderator")
    public ResponseEntity<ParticipantResponse> joinRoom(@PathVariable Long ticketId) {
        User user = resolveCurrentUser();
        log.info("[DIAGNOSTIC] POST /api/v1/chat/{}/join - User: {}, UserId: {}", ticketId, user.getUsername(), user.getId());
        try {
            SupportSessionParticipant participant = chatSessionService.joinRoom(ticketId, user, ParticipantRole.MODERATOR, true);
            log.info("[DIAGNOSTIC] POST /api/v1/chat/{}/join SUCCESS - ParticipantId: {}, IsActive: {}",
                    ticketId, participant.getId(), participant.isActive());
            return ResponseEntity.ok(ParticipantResponse.builder()
                    .id(participant.getId())
                    .ticketId(ticketId)
                    .sessionId(participant.getSession().getId())
                    .userId(user.getId())
                    .username(user.getUsername())
                    .role(participant.getRole().name())
                    .joinedAt(participant.getJoinedAt())
                    .leftAt(participant.getLeftAt())
                    .isActive(participant.isActive())
                    .isOnline(true)
                    .build());
        } catch (EntityNotFoundException e) {
            log.error("[DIAGNOSTIC] POST /api/v1/chat/{}/join EntityNotFound: {}", ticketId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (AccessDeniedException e) {
            log.error("[DIAGNOSTIC] POST /api/v1/chat/{}/join AccessDenied: {}", ticketId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage(), e);
        } catch (IllegalStateException e) {
            log.error("[DIAGNOSTIC] POST /api/v1/chat/{}/join IllegalState: {}", ticketId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @PostMapping("/{ticketId}/leave")
    @Operation(summary = "Leave support room")
    public ResponseEntity<ParticipantResponse> leaveRoom(@PathVariable Long ticketId) {
        User user = resolveCurrentUser();
        SupportSessionParticipant participant = chatSessionService.leaveRoom(ticketId, user);
        return ResponseEntity.ok(ParticipantResponse.builder()
                .id(participant.getId())
                .ticketId(ticketId)
                .sessionId(participant.getSession().getId())
                .userId(user.getId())
                .username(user.getUsername())
                .role(participant.getRole().name())
                .joinedAt(participant.getJoinedAt())
                .leftAt(participant.getLeftAt())
                .isActive(participant.isActive())
                .isOnline(false)
                .build());
    }

    @GetMapping("/{ticketId}/participants")
    @Operation(summary = "Get active support room participants")
    public ResponseEntity<List<ParticipantResponse>> getParticipants(@PathVariable Long ticketId) {
        Long tenantId = tenantResolver.resolveTenantId();
        List<ParticipantResponse> result = chatSessionService.getParticipants(ticketId, tenantId);
        log.info("[DIAGNOSTIC] GET /api/v1/chat/{}/participants - TenantId: {}, Returned count: {}",
                ticketId, tenantId, result.size());
        return ResponseEntity.ok(result);
    }

    private User resolveCurrentUser() {
        Long tenantId = tenantResolver.resolveTenantId();
        CustomUserDetails details = authenticationFacade.getCurrentUserDetails();
        if (tenantId == null || details == null) {
            throw new AccessDeniedException("Authenticated tenant context is required");
        }
        return userRepository.findByIdAndDeletedFalse(details.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + details.getUserId()));
    }
}
