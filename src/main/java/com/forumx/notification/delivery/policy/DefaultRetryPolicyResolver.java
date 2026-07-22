package com.forumx.notification.delivery.policy;

import com.forumx.notification.delivery.entity.DeliveryChannel;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class DefaultRetryPolicyResolver implements RetryPolicyResolver {

    private final Map<DeliveryChannel, RetryPolicy> policyMap;
    private final ConfigurableRetryPolicy defaultPolicy;

    public DefaultRetryPolicyResolver(List<RetryPolicy> policies, ConfigurableRetryPolicy defaultPolicy) {
        this.defaultPolicy = defaultPolicy;
        this.policyMap = policies.stream()
                .filter(p -> p.getChannel() != null)
                .collect(Collectors.toMap(RetryPolicy::getChannel, p -> p, (p1, p2) -> p1));
    }

    @Override
    public RetryPolicy resolvePolicy(DeliveryChannel channel) {
        return policyMap.getOrDefault(channel, defaultPolicy);
    }
}
