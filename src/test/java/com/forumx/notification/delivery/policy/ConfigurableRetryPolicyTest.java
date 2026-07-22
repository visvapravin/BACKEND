package com.forumx.notification.delivery.policy;

import static org.junit.jupiter.api.Assertions.*;

import com.forumx.notification.delivery.config.DeliveryRetryProperties;
import com.forumx.notification.delivery.exception.PermanentDeliveryException;
import com.forumx.notification.delivery.exception.RetryableDeliveryException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ConfigurableRetryPolicyTest {

    private ConfigurableRetryPolicy retryPolicy;

    @BeforeEach
    public void setUp() {
        DeliveryRetryProperties properties = new DeliveryRetryProperties();
        properties.setMaxAttempts(4);
        properties.setAttempt1DelaySeconds(30);
        properties.setAttempt2DelaySeconds(120);
        properties.setAttempt3DelaySeconds(600);
        retryPolicy = new ConfigurableRetryPolicy(properties);
    }

    @Test
    public void testShouldRetryRetryableException() {
        assertTrue(retryPolicy.shouldRetry(1, new RetryableDeliveryException("Network timeout")));
        assertTrue(retryPolicy.shouldRetry(3, new RetryableDeliveryException("Connection refused")));
        assertFalse(retryPolicy.shouldRetry(4, new RetryableDeliveryException("Max attempts reached")));
    }

    @Test
    public void testShouldNotRetryPermanentException() {
        assertFalse(retryPolicy.shouldRetry(1, new PermanentDeliveryException("Invalid recipient address")));
    }

    @Test
    public void testGetNextRetryDelay() {
        assertEquals(Duration.ofSeconds(30), retryPolicy.getNextRetryDelay(1));
        assertEquals(Duration.ofSeconds(120), retryPolicy.getNextRetryDelay(2));
        assertEquals(Duration.ofSeconds(600), retryPolicy.getNextRetryDelay(3));
        assertEquals(Duration.ZERO, retryPolicy.getNextRetryDelay(4));
    }
}
