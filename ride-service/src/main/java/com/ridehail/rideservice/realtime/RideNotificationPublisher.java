package com.ridehail.rideservice.realtime;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Port of config/rabitmq.js sendNotification(): publishes a JSON body to the default exchange
 * with routing key = queue name (rabbit:push-notification), exactly like
 * {@code channel.sendToQueue(RABBIT_QUEUE_KEY.PUSH_NOTIFICATION, Buffer.from(JSON.stringify(data)))}.
 * Never throws - the legacy helper only logs on failure.
 */
@Component
public class RideNotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(RideNotificationPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RideNotificationPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendNotification(Map<String, ?> data) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(data);
            rabbitTemplate.convertAndSend(RideRabbitConfig.PUSH_NOTIFICATION_QUEUE, body);
        } catch (Exception e) {
            log.warn("Error on sendNotification----> {}", e.getMessage());
        }
    }
}
