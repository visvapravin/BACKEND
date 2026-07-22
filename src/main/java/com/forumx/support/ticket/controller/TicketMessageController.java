package com.forumx.support.ticket.controller;

import com.forumx.support.ticket.dto.request.CreateTicketMessageRequest;
import com.forumx.support.ticket.dto.response.TicketMessageResponse;
import com.forumx.support.ticket.service.TicketMessageService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/support/tickets/{ticketId}/messages")
@Tag(name = "Support Tickets", description = "Private customer support ticket operations.")
public class TicketMessageController {

    private final TicketMessageService ticketMessageService;

    @PostMapping
    @Operation(summary = "Send ticket message")
    public ResponseEntity<TicketMessageResponse> sendMessage(
            @PathVariable Long ticketId,
            @Valid @RequestBody CreateTicketMessageRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ticketMessageService.sendMessage(ticketId, request));
    }

    @GetMapping
    @Operation(summary = "Get ticket conversation")
    public ResponseEntity<Page<TicketMessageResponse>> getConversation(
            @PathVariable Long ticketId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ResponseEntity.ok(ticketMessageService.getConversation(ticketId, pageable));
    }
}
