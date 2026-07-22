package com.forumx.support.ticket.dto.request;

import com.forumx.support.ticket.entity.TicketStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTicketStatusRequest {
    @NotNull
    private TicketStatus status;
}
