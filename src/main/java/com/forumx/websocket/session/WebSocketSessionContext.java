package com.forumx.websocket.session;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WebSocketSessionContext {
    public static final String SESSION_KEY = "WS_SESSION_CONTEXT";

    private final String sessionId;
    private final Long userId;
    private final String username;
    private final Long tenantId;
    private final List<String> roles;
}
