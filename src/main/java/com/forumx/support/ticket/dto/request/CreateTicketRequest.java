package com.forumx.support.ticket.dto.request;

import com.forumx.support.ticket.entity.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class CreateTicketRequest {
    @NotBlank
    @Size(max = 255)
    private String subject;

    @NotBlank
    private String description;

    private TicketPriority priority;
}
