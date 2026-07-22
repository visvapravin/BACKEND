package com.forumx.support.queue.mapper;

import com.forumx.support.queue.event.SupportQueueEvent;
import com.forumx.websocket.dto.RealtimeEvent;
import org.springframework.stereotype.Component;

@Component
public class SupportQueueRealtimeMapper {

    public RealtimeEvent<SupportQueueEvent> toRealtimeEvent(String eventType, SupportQueueEvent event) {
        return RealtimeEvent.<SupportQueueEvent>builder()
                .type(eventType)
                .payload(event)
                .build();
    }
}

