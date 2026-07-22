package com.forumx.support.ticket.service;

import com.forumx.support.ticket.dto.request.AssignTicketRequest;
import com.forumx.support.ticket.dto.request.CreateTicketRequest;
import com.forumx.support.ticket.dto.request.UpdateTicketStatusRequest;
import com.forumx.support.ticket.dto.response.TicketResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TicketService {
    TicketResponse createTicket(CreateTicketRequest request);
    TicketResponse getTicket(Long ticketId);
    Page<TicketResponse> getMyTickets(Pageable pageable);
    TicketResponse updateStatus(Long ticketId, UpdateTicketStatusRequest request);
    TicketResponse assignTicket(Long ticketId, AssignTicketRequest request);
}
