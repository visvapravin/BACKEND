package com.forumx.support.ticket.mapper;

import com.forumx.support.ticket.dto.request.CreateTicketRequest;
import com.forumx.support.ticket.dto.response.TicketResponse;
import com.forumx.support.ticket.entity.Ticket;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface TicketMapper {
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "subject", source = "subject")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "priority", source = "priority")
    Ticket toEntity(CreateTicketRequest request);

    @Mapping(target = "createdBy", source = "creator.id")
    @Mapping(target = "assignedTo", source = "assignedTo.id")
    TicketResponse toResponse(Ticket ticket);
}
