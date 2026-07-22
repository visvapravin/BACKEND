package com.forumx.support.ticket.dto.response;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketMessageResponse {
    private Long id;
    private Long ticketId;
    private Long senderId;
    private String senderName;
    private String message;
    private UUID messageUuid;
    private Instant createdAt;
    private Instant updatedAt;
}
