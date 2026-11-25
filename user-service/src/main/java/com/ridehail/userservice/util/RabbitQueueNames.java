package com.ridehail.userservice.util;

/** Mirrors helpers/user.constants.js's RABBIT_QUEUE_KEY map. */
public final class RabbitQueueNames {

    public static final String MAIL_NOTIFICATION = "rabbit:mail-notification-queue";

    public static final String PUSH_NOTIFICATION = "rabbit:push-notification";

    private RabbitQueueNames() {
    }
}
