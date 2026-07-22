package com.forumx.websocket.gateway;

import com.forumx.websocket.dto.RealtimeEvent;

public interface RealtimeGateway {

    /**
     * Sends a real-time event to a specific user.
     *
     * @param username    the target user username
     * @param destination the specific user destination (e.g. /queue/notifications)
     * @param event       the event payload wrapped in RealtimeEvent
     */
    <T> void sendToUser(String username, String destination, RealtimeEvent<T> event);

    /**
     * Broadcasts a real-time event to a public topic.
     *
     * @param destination the topic destination (e.g. /topic/notifications)
     * @param event       the event payload wrapped in RealtimeEvent
     */
    <T> void sendToTopic(String destination, RealtimeEvent<T> event);
}
