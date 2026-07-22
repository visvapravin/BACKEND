package com.forumx.support.chat.mapper;

import com.forumx.support.chat.event.durable.ChatEvent;
import com.forumx.websocket.dto.RealtimeEvent;
import org.springframework.stereotype.Component;

@Component
public class ChatRealtimeMapper {

    public RealtimeEvent<ChatEvent> toRealtimeEvent(String eventType, ChatEvent event) {
        return RealtimeEvent.<ChatEvent>builder()
                .type(eventType)
                .payload(event)
                .build();
    }
}
