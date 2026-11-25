package com.ridehail.driverservice.realtime;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Port of config/rabitmq.js queue assertions used by driverWallet.controller.js's
 * notifyDriverClaimUpdate. The legacy code calls plain {@code channel.assertQueue(key)}
 * (amqplib), whose durable default is {@code false}, so the queue is declared non-durable here to
 * match. Declaring the same queue name as ride-service's RideRabbitConfig is harmless/idempotent -
 * both just assert the same broker queue.
 */
@Configuration
public class DriverWalletRabbitConfig {

    public static final String PUSH_NOTIFICATION_QUEUE = "rabbit:push-notification";

    @Bean
    public Queue driverWalletPushNotificationQueue() {
        return new Queue(PUSH_NOTIFICATION_QUEUE, false);
    }
}
