package com.forumx.websocket.constant;

public final class WebSocketDestinations {
    private WebSocketDestinations() {}

    public static final String APP_PREFIX = "/app";
    public static final String USER_PREFIX = "/user";
    public static final String TOPIC_PREFIX = "/topic";
    public static final String QUEUE_PREFIX = "/queue";

    public static final String QUEUE_ERRORS = QUEUE_PREFIX + "/errors";
    public static final String TOPIC_NOTIFICATIONS = TOPIC_PREFIX + "/notifications";

    public static String getTenantTopic(Long tenantId, String subTopic) {
        return String.format("%s/tenant/%d/%s", TOPIC_PREFIX, tenantId, subTopic);
    }

    public static String getTenantUserQueue(Long tenantId, String username, String subQueue) {
        return String.format("%s/tenant/%d/user/%s/%s", QUEUE_PREFIX, tenantId, username, subQueue);
    }
}
