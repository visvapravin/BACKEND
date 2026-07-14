package com.forumx.infrastructure.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@ActiveProfiles("dev")
public class KafkaConfigurationDisabledTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void testKafkaBeansAreAbsentInDevProfile() {
        assertFalse(applicationContext.containsBean("kafkaAdmin"), "KafkaAdmin should not exist in dev profile");
        assertFalse(applicationContext.containsBean("kafkaTemplate"), "KafkaTemplate should not exist in dev profile");
        assertFalse(applicationContext.containsBean("notificationTopic"), "notificationTopic should not exist in dev profile");
        assertFalse(applicationContext.containsBean("emailTopic"), "emailTopic should not exist in dev profile");
        assertFalse(applicationContext.containsBean("retryTopic"), "retryTopic should not exist in dev profile");
        assertFalse(applicationContext.containsBean("dlqTopic"), "dlqTopic should not exist in dev profile");
        
        // Also check by type to be absolutely sure
        assertFalse(applicationContext.getBeansOfType(KafkaAdmin.class).size() > 0, "No beans of type KafkaAdmin should exist");
        assertFalse(applicationContext.getBeansOfType(KafkaTemplate.class).size() > 0, "No beans of type KafkaTemplate should exist");
        assertFalse(applicationContext.getBeansOfType(NewTopic.class).size() > 0, "No beans of type NewTopic should exist");
    }
}
