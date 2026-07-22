package com.forumx.notification.delivery.strategy;

import com.forumx.notification.delivery.entity.DeliveryChannel;
import com.forumx.notification.delivery.exception.UnsupportedDeliveryChannelException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class DeliveryStrategyRegistry {

    private final Map<DeliveryChannel, DeliveryStrategy> strategyMap;

    public DeliveryStrategyRegistry(List<DeliveryStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(DeliveryStrategy::getChannel, Function.identity(), (s1, s2) -> s1));
    }

    public DeliveryStrategy getStrategy(DeliveryChannel channel) {
        DeliveryStrategy strategy = strategyMap.get(channel);
        if (strategy == null) {
            throw new UnsupportedDeliveryChannelException(channel);
        }
        return strategy;
    }
}
