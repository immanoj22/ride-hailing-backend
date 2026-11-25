package com.ridehail.adminservice.service;

import java.util.Map;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.ridehail.adminservice.config.RabbitConfig;

/**
 * Direct port of {@code sendNotification(data)} in Admin-service/config/rabitmq.js:
 * {@code channel.sendToQueue("rabbit:push-notification", Buffer.from(JSON.stringify(data)))}.
 * Publishes to the default exchange with routing key == queue name. Failures are swallowed and
 * logged, exactly like the original (a broker outage never breaks the calling request).
 */
@Component
public class NotificationQueuePublisher {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NotificationQueuePublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public NotificationQueuePublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendNotification(Map<String, Object> data) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.PUSH_NOTIFICATION_QUEUE, data);
        } catch (Exception e) {
            log.warn("Error on sendNotification: {}", e.getMessage());
        }
    }
}
