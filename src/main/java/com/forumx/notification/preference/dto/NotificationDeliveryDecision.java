package com.forumx.notification.preference.dto;

import java.util.Set;
import com.forumx.notification.preference.domain.DecisionReason;
import com.forumx.notification.preference.domain.DeliveryChannel;

public record NotificationDeliveryDecision(
        Set<DeliveryChannel> enabledChannels,
        Set<DecisionReason> reasons
) {
    public boolean isChannelEnabled(DeliveryChannel channel) {
        return enabledChannels != null && enabledChannels.contains(channel);
    }
}
