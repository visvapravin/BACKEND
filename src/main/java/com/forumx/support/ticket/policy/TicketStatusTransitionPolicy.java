package com.forumx.support.ticket.policy;

import com.forumx.support.ticket.entity.TicketStatus;
import org.springframework.stereotype.Component;

@Component
public class TicketStatusTransitionPolicy {

    public boolean isValidTransition(TicketStatus currentStatus, TicketStatus newStatus) {
        if (currentStatus == newStatus) {
            return true;
        }
        return switch (currentStatus) {
            case OPEN -> newStatus == TicketStatus.IN_PROGRESS || newStatus == TicketStatus.RESOLVED || newStatus == TicketStatus.CLOSED;
            case IN_PROGRESS -> newStatus == TicketStatus.RESOLVED || newStatus == TicketStatus.CLOSED;
            case RESOLVED -> newStatus == TicketStatus.CLOSED || newStatus == TicketStatus.REOPENED;
            case CLOSED -> newStatus == TicketStatus.REOPENED;
            case REOPENED -> newStatus == TicketStatus.IN_PROGRESS || newStatus == TicketStatus.RESOLVED || newStatus == TicketStatus.CLOSED;
        };
    }

    public void validateTransition(TicketStatus currentStatus, TicketStatus newStatus) {
        if (!isValidTransition(currentStatus, newStatus)) {
            throw new IllegalStateException(String.format("Invalid ticket status transition from %s to %s", currentStatus, newStatus));
        }
    }
}


