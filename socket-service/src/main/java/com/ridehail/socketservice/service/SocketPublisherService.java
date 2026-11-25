package com.ridehail.socketservice.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehail.common.realtime.PubSubChannels;

/**
 * Port of helper/publisher.js. Each client-emitted ride/driver event is republished verbatim as
 * {@code JSON.stringify({ body: <event data>, driver: <socket userData> })} onto the matching
 * {@link PubSubChannels} channel; ride-service / driver-service subscribe and run the business
 * logic (their Redis subscribers are unchanged by this migration).
 */
@Service
public class SocketPublisherService {

    private static final Logger log = LoggerFactory.getLogger(SocketPublisherService.class);
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SocketPublisherService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** {@code { body: data, driver: driverData }} - matches publisher.js payload shape. */
    public void publish(String channel, Object body, Object driver) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("body", body);
            payload.put("driver", driver);
            redisTemplate.convertAndSend(channel, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.error("Failed to publish to channel {}", channel, e);
        }
    }

    public void onUpdateDriverLocation(Object body, String userId) {
        Map<String, Object> driver = new LinkedHashMap<>();
        driver.put("_id", userId);
        publish(PubSubChannels.UPDATE_DRIVER_LOCATION, body, driver);
    }

    public void onRideAccept(Object body, Object driver) {
        publish(PubSubChannels.RIDE_ACCEPTED, body, driver);
    }

    public void onRidePickup(Object body, Object driver) {
        publish(PubSubChannels.RIDE_PICKUP, body, driver);
    }

    public void onRideStart(Object body, Object driver) {
        publish(PubSubChannels.RIDE_START, body, driver);
    }

    public void onRideDropoff(Object body, Object driver) {
        publish(PubSubChannels.RIDE_DROPOFF, body, driver);
    }

    public void onRidePayment(Object body, Object driver) {
        publish(PubSubChannels.RIDE_PAYMENT, body, driver);
    }

    public void onRideEnd(Object body, Object driver) {
        publish(PubSubChannels.RIDE_ENDTRIP, body, driver);
    }

    public void onRideUpdateVehicleType(Object body, Object driver) {
        publish(PubSubChannels.RIDE_UPDATE_VEHICLETYPE, body, driver);
    }

    public void onRideCancelByUser(Object body, Object driver) {
        publish(PubSubChannels.RIDE_CANCELBY_USER, body, driver);
    }

    public void onRideCancelByDriver(Object body, Object driver) {
        publish(PubSubChannels.RIDE_CANCELBY_DRIVER, body, driver);
    }

    public void onRideFetchByUser(Object body, Object driver) {
        publish(PubSubChannels.RIDE_FETCHBY_USER, body, driver);
    }

    public void onRideIgnoreByDriver(Object body, Object driver) {
        publish(PubSubChannels.RIDE_IGNORE_DRIVER, body, driver);
    }

    public void onRideSuddenEnd(Object body, Object driver) {
        publish(PubSubChannels.RIDE_SUDDEN_END, body, driver);
    }

    public void onUpdateDriverGPS(Object body, Object driver) {
        publish(PubSubChannels.DRIVER_GPS_UPDATE, body, driver);
    }
}
