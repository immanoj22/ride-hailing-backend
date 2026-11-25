package com.ridehail.rideservice.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehail.rideservice.constants.RideConstants;

/**
 * Port of helpers/ride.publisher.js onUpdateDriver(data): publishes JSON.stringify(data) to the
 * Redis channel PUBSUB_KEY.UPDATE_DRIVER ("pubsub:driver:update"). driver-service subscribes to
 * this to flip driver trip/onride status. Never throws - the legacy helper only logs.
 */
@Component
public class RideDriverUpdatePublisher {

    private static final Logger log = LoggerFactory.getLogger(RideDriverUpdatePublisher.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RideDriverUpdatePublisher(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void onUpdateDriver(Object data) {
        try {
            redisTemplate.convertAndSend(RideConstants.PUBSUB_UPDATE_DRIVER, objectMapper.writeValueAsString(data));
        } catch (Exception e) {
            log.error("Error on onUpdateDriver----> {}", e.getMessage());
        }
    }
}
