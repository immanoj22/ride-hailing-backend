package com.ridehail.adminservice.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Port of Admin-service/config/rabitmq.js. That file is producer-only: it asserts both queues
 * (RABBIT_QUEUE_KEY) on connect and only ever {@code sendToQueue("rabbit:push-notification", ...)}
 * (send_coupon / push_notification). No consumers exist in this service.
 */
@Configuration
public class RabbitConfig {

    public static final String PUSH_NOTIFICATION_QUEUE = "rabbit:push-notification";
    public static final String MAIL_NOTIFICATION_QUEUE = "rabbit:mail-notification-queue";

    @Bean
    public Queue pushNotificationQueue() {
        return QueueBuilder.durable(PUSH_NOTIFICATION_QUEUE).build();
    }

    @Bean
    public Queue mailNotificationQueue() {
        return QueueBuilder.durable(MAIL_NOTIFICATION_QUEUE).build();
    }

    /** Publish JSON bodies, matching the original {@code Buffer.from(JSON.stringify(data))}. */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
