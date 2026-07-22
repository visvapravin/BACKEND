package com.forumx.support.chat.controller;

import com.forumx.support.chat.dto.request.SendMessageRequest;
import com.forumx.support.chat.dto.response.ChatMessageResponse;
import com.forumx.support.chat.dto.response.ChatSessionResponse;
import com.forumx.support.chat.entity.ChatMessage;
import com.forumx.support.chat.entity.ChatSession;
import com.forumx.support.chat.mapper.ChatMessageMapper;
import com.forumx.support.chat.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat")
@Tag(name = "Support Chat", description = "Live support chat session and messaging operations.")
public class ChatController {

    private final ChatService chatService;
    private final ChatMessageMapper chatMessageMapper;

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
            if (e.getMessage().contains("not assigned")) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
            } else if (e.getMessage().contains("closed")) {
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
        ChatSession session = chatService.getSession(ticketId);
        return ResponseEntity.ok(chatMessageMapper.toResponse(session));
    }
}
