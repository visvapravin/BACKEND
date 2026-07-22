package com.forumx.notification.delivery.policy;

import com.forumx.notification.delivery.entity.DeliveryChannel;

public interface RetryPolicyResolver {
    RetryPolicy resolvePolicy(DeliveryChannel channel);
}
