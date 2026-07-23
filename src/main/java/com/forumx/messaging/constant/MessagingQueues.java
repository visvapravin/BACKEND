package com.forumx.messaging.constant;

public final class MessagingQueues {
    private MessagingQueues() {}

    public static final String USER_NOTIFICATION_QUEUE = "forumx.user.notification.queue";
    public static final String USER_NOTIFICATION_DLQ = "forumx.user.notification.queue.dlq";

    public static final String SUPPORT_QUEUE = "forumx.support.queue";
    public static final String SUPPORT_DLQ = "forumx.support.queue.dlq";

    public static final String CHAT_QUEUE = "forumx.chat.queue";
    public static final String CHAT_DLQ = "forumx.chat.queue.dlq";

    public static final String EMAIL_QUEUE = "forumx.notification.email.queue";
    public static final String EMAIL_DLQ = "forumx.notification.email.queue.dlq";

    public static final String DELIVERY_RETRY_QUEUE = "forumx.notification.retry.queue";
    public static final String DELIVERY_RETRY_DLQ = "forumx.notification.retry.queue.dlq";
    public static final String DELIVERY_DEAD_QUEUE = "forumx.notification.dead.queue";
}
