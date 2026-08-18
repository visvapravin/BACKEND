package com.forumx.support.ticket.controller;

import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.support.ticket.dto.request.AssignTicketRequest;
import com.forumx.support.ticket.dto.request.CreateTicketRequest;
import com.forumx.support.ticket.dto.request.UpdateTicketStatusRequest;
import com.forumx.support.ticket.dto.response.TicketResponse;
import com.forumx.support.ticket.service.TicketService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/support/tickets")
@Tag(name = "Support Tickets", description = "Private customer support ticket operations.")
public class TicketController {

    private final TicketService ticketService;
    private final AuthenticationFacade authenticationFacade;

    @PostMapping
    @Operation(summary = "Create support ticket")
    public ResponseEntity<TicketResponse> createTicket(@Valid @RequestBody CreateTicketRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.createTicket(request));
    }

    @GetMapping
    @Operation(summary = "List support tickets")
    public ResponseEntity<Page<TicketResponse>> getMyTickets(
            @RequestParam(required = false) com.forumx.support.ticket.entity.TicketStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ticketService.getMyTickets(status, pageable));
    }

    @GetMapping("/dashboard/summary")
    @Operation(summary = "Get moderator support dashboard summary")
    public ResponseEntity<com.forumx.support.ticket.dto.response.SupportDashboardSummary> getDashboardSummary() {
        return ResponseEntity.ok(ticketService.getDashboardSummary());
    }

    @GetMapping("/{ticketId}")
    @Operation(summary = "Get support ticket")
    public ResponseEntity<TicketResponse> getTicket(@PathVariable Long ticketId) {
        return ResponseEntity.ok(ticketService.getTicket(ticketId));
    }

    @PutMapping("/{ticketId}/status")
    @Operation(summary = "Update ticket status")
    public ResponseEntity<TicketResponse> updateStatus(@PathVariable Long ticketId,
                                                       @Valid @RequestBody UpdateTicketStatusRequest request) {
        return ResponseEntity.ok(ticketService.updateStatus(ticketId, request));
    }

    @PutMapping("/{ticketId}/assign")
    @Operation(summary = "Assign support ticket lead moderator")
    public ResponseEntity<TicketResponse> assignTicket(@PathVariable Long ticketId,
                                                        @Valid @RequestBody AssignTicketRequest request) {
        return ResponseEntity.ok(ticketService.assignTicket(ticketId, request));
    }

    @PostMapping("/{ticketId}/claim")
    @Operation(summary = "Take ownership of ticket as lead moderator")
    public ResponseEntity<TicketResponse> claimOwnership(@PathVariable Long ticketId) {
        CustomUserDetails details = authenticationFacade.getCurrentUserDetails();
        AssignTicketRequest request = new AssignTicketRequest();
        request.setAssignedToUserId(details.getUserId());
        return ResponseEntity.ok(ticketService.assignTicket(ticketId, request));
    }
}
