package com.forumx.support.ticket.mapper;

import com.forumx.support.ticket.dto.request.CreateTicketMessageRequest;
import com.forumx.support.ticket.dto.response.TicketMessageResponse;
import com.forumx.support.ticket.entity.TicketMessage;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface TicketMessageMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "message", source = "message")
    TicketMessage toEntity(CreateTicketMessageRequest request);

    @Mapping(target = "ticketId", source = "ticket.id")
    @Mapping(target = "senderId", source = "sender.id")
    @Mapping(target = "senderName", source = "sender.username")
    TicketMessageResponse toResponse(TicketMessage message);
}
