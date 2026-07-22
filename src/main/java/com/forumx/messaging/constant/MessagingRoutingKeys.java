package com.forumx.messaging.constant;

public final class MessagingRoutingKeys {
    private MessagingRoutingKeys() {}

    public static final String USER_CREATED = "user.created";
    public static final String QUESTION_CREATED = "question.created";
    public static final String NOTIFICATION_CREATED = "notification.created";
    public static final String SUPPORT_CREATED = "support.created";

    public static final String SUPPORT_QUEUED = "support.ticket.queued";
    public static final String SUPPORT_CLAIMED = "support.ticket.claimed";
    public static final String SUPPORT_STATUS_CHANGED = "support.ticket.status_changed";

    public static final String CHAT_MESSAGE_SENT = "chat.message.sent";
    public static final String CHAT_MESSAGE_DELETED = "chat.message.deleted";
    public static final String CHAT_MESSAGE_READ = "chat.message.read";

    public static final String EMAIL_SEND = "notification.email.send";
    public static final String EMAIL_SENT = "notification.email.sent";
    public static final String EMAIL_FAILED = "notification.email.failed";

    public static final String DELIVERY_RETRY = "notification.delivery.retry";
    public static final String DELIVERY_DEAD = "notification.delivery.dead";
}

