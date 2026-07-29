package com.forumx.support.chat.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypingPayload {
    private String action; // START or STOP
    private Long sessionId;
    private Long userId;
    private String username;
    private String displayName;

    public TypingPayload(String action) {
        this.action = action;
    }
}

