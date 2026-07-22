package com.forumx.notification.mapper;

import com.forumx.notification.dto.response.NotificationResponse;
import com.forumx.notification.entity.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface NotificationMapper {

    @Mapping(target = "recipientId", source = "recipient.id")
    @Mapping(target = "actorId", source = "actor.id")
    NotificationResponse toResponse(Notification notification);

    @Mapping(target = "notificationId", source = "id")
    @Mapping(target = "type", source = "notificationType")
    @Mapping(target = "isRead", source = "read")
    com.forumx.websocket.dto.WebSocketNotificationDto toWebSocketDto(Notification notification);
}
