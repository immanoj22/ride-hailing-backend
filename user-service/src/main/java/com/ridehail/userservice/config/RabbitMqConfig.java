package com.ridehail.userservice.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ridehail.userservice.util.RabbitQueueNames;

/**
 * Declares the two queues config/rabitmq.js asserted at startup. MAIL_NOTIFICATION is asserted but
 * never published to anywhere in the reviewed Node source (dead/reserved) - kept declared-but-unused
 * here for parity. PUSH_NOTIFICATION is produced to by the wallet-referral-rewards bucket's
 * add_referer (user.controller.js's sendNotification call).
 */
@Configuration
public class RabbitMqConfig {

    @Bean
    public Queue mailNotificationQueue() {
        return new Queue(RabbitQueueNames.MAIL_NOTIFICATION, true);
    }

    @Bean
    public Queue pushNotificationQueue() {
        return new Queue(RabbitQueueNames.PUSH_NOTIFICATION, true);
    }
}
