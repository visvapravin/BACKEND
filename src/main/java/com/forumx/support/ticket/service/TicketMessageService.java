package com.forumx.support.ticket.service;

import com.forumx.support.ticket.dto.request.CreateTicketMessageRequest;
import com.forumx.support.ticket.dto.response.TicketMessageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TicketMessageService {
    TicketMessageResponse sendMessage(Long ticketId, CreateTicketMessageRequest request);
    Page<TicketMessageResponse> getConversation(Long ticketId, Pageable pageable);
}
