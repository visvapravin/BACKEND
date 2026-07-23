package com.forumx.websocket.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealtimeEvent<T> {
    @Builder.Default
    private UUID eventId = UUID.randomUUID();
    
    @Builder.Default
    private Instant timestamp = Instant.now();
    
    private String type;
    private T payload;

    public RealtimeEvent(String type, T payload) {
        this.eventId = UUID.randomUUID();
        this.timestamp = Instant.now();
        this.type = type;
        this.payload = payload;
    }
}
