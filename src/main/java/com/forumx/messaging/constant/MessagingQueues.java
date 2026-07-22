package com.forumx.messaging.constant;

public final class MessagingQueues {
    private MessagingQueues() {}

    public static final String USER_NOTIFICATION_QUEUE = "forumx.queue.user.notification";
    public static final String USER_NOTIFICATION_DLQ = "forumx.queue.user.notification.dlq";

    public static final String SUPPORT_QUEUE = "forumx.queue.support";
    public static final String SUPPORT_DLQ = "forumx.queue.support.dlq";

    public static final String CHAT_QUEUE = "forumx.queue.chat";
    public static final String CHAT_DLQ = "forumx.queue.chat.dlq";

    public static final String EMAIL_QUEUE = "forumx.queue.email";
    public static final String EMAIL_DLQ = "forumx.queue.email.dlq";

    public static final String DELIVERY_RETRY_QUEUE = "forumx.retry.queue";
    public static final String DELIVERY_RETRY_DLQ = "forumx.retry.dlq";
    public static final String DELIVERY_DEAD_QUEUE = "forumx.dead.queue";
}

