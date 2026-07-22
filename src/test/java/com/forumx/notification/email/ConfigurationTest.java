package com.forumx.notification.email;

import static org.junit.jupiter.api.Assertions.*;

import com.forumx.ForumXApplication;
import com.forumx.notification.email.config.MailProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("dev")
@SpringBootTest(classes = ForumXApplication.class, properties = "management.health.mail.enabled=false")
public class ConfigurationTest {

    @MockBean private com.forumx.redis.gateway.RedisGateway redisGateway;
    @MockBean private com.forumx.websocket.gateway.RealtimeGateway realtimeGateway;

    @Autowired
    private MailProperties mailProperties;

    @Test
    public void testMailPropertiesBinding() {
        assertNotNull(mailProperties);
        assertNotNull(mailProperties.getHost());
        assertTrue(mailProperties.getPort() > 0);
        assertNotNull(mailProperties.getFrom());
    }
}
