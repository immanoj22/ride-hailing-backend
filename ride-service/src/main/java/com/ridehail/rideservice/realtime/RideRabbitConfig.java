package com.ridehail.rideservice.realtime;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Port of config/rabitmq.js queue assertions. The legacy code calls plain
 * {@code channel.assertQueue(key)} (amqplib), whose durable default is {@code false}, so both
 * queues are declared non-durable here to match. Only PUSH_NOTIFICATION is actually produced to
 * (sendNotification); MAIL_NOTIFICATION is declared but unused, same as the original. No
 * consumers exist in this service.
 */
@Configuration
public class RideRabbitConfig {

    public static final String PUSH_NOTIFICATION_QUEUE = "rabbit:push-notification";
    public static final String MAIL_NOTIFICATION_QUEUE = "rabbit:mail-notification-queue";

    @Bean
    public Queue ridePushNotificationQueue() {
        return new Queue(PUSH_NOTIFICATION_QUEUE, false);
    }

    @Bean
    public Queue rideMailNotificationQueue() {
        return new Queue(MAIL_NOTIFICATION_QUEUE, false);
    }
}
