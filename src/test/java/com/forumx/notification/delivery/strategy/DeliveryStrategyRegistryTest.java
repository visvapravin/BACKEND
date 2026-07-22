package com.forumx.notification.delivery.strategy;

import static org.junit.jupiter.api.Assertions.*;

import com.forumx.notification.delivery.entity.DeliveryChannel;
import com.forumx.notification.delivery.exception.UnsupportedDeliveryChannelException;
import com.forumx.notification.delivery.strategy.impl.EmailDeliveryStrategy;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DeliveryStrategyRegistryTest {

    @Mock
    private EmailDeliveryStrategy emailDeliveryStrategy;

    private DeliveryStrategyRegistry registry;

    @BeforeEach
    public void setUp() {
        org.mockito.Mockito.when(emailDeliveryStrategy.getChannel()).thenReturn(DeliveryChannel.EMAIL);
        registry = new DeliveryStrategyRegistry(List.of(emailDeliveryStrategy));
    }

    @Test
    public void testGetStrategySuccess() {
        DeliveryStrategy strategy = registry.getStrategy(DeliveryChannel.EMAIL);
        assertNotNull(strategy);
        assertEquals(DeliveryChannel.EMAIL, strategy.getChannel());
    }

    @Test
    public void testGetStrategyThrowsUnsupportedException() {
        assertThrows(UnsupportedDeliveryChannelException.class, () -> registry.getStrategy(DeliveryChannel.PUSH));
    }
}
