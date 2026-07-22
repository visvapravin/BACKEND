package com.forumx.websocket.config;

import static org.junit.jupiter.api.Assertions.*;

import com.forumx.ForumXApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = ForumXApplication.class)
@ActiveProfiles("dev")
public class WebSocketConfigTest {

    @Autowired
    private WebSocketProperties webSocketProperties;

    @Autowired
    private WebSocketConfig webSocketConfig;

    @Test
    public void testWebSocketPropertiesLoading() {
        assertNotNull(webSocketProperties);
        assertNotNull(webSocketProperties.getAllowedOrigins());
        assertFalse(webSocketProperties.getAllowedOrigins().isEmpty());
        // Default allowed origin defined in dev profile or properties fallback
        assertTrue(webSocketProperties.getAllowedOrigins().contains("http://localhost:5173"));
    }

    @Test
    public void testWebSocketConfigLoading() {
        assertNotNull(webSocketConfig);
    }
}
