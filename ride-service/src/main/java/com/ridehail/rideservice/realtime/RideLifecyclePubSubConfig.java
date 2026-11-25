package com.ridehail.rideservice.realtime;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehail.common.realtime.PubSubChannels;
import com.ridehail.rideservice.service.RideLifecycleService;

/**
 * Port of helpers/ride.subscriber.js - registers one Redis subscriber over EVERY PUBSUB_KEY
 * channel that file subscribed to and dispatches the JSON payload to the same service method the
 * legacy handler called (via ride.controller). socket-service publishes plain JSON to these
 * channels when a mobile client fires the corresponding socket event.
 *
 * Channel -> method mapping (ride.subscriber.js startSubscriber / subscribeObj):
 *   pubsub:ride:accepted            -> acceptRide
 *   pubsub:ride:update:pickup       -> pickupRide
 *   pubsub:ride:update:start        -> startRide
 *   pubsub:ride:update:dropoff      -> dropoffRide
 *   pubsub:ride:update:payment      -> rideCompletePayment
 *   pubsub:ride:update:endtrip      -> endRide
 *   pubsub:ride:update:vehicletype  -> updateVehicleType
 *   pubsub:ride:cancelby:user       -> cancelRideByUser
 *   pubsub:ride:cancelby:driver     -> rideCancelByDriver
 *   pubsub:ride:fetch:user          -> fetchRide
 *   pubsub:ride:ignore:driver       -> updateIgnoredDriver
 *   pubsub:ride:sudden:end          -> suddenendByUser
 *   pubsub:driver:gps:update        -> addGPSPoint
 */
@Configuration
public class RideLifecyclePubSubConfig {

    private static final Logger log = LoggerFactory.getLogger(RideLifecyclePubSubConfig.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public RedisMessageListenerContainer rideLifecycleListenerContainer(
            RedisConnectionFactory connectionFactory, RideLifecycleService service) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        MessageListener listener = (Message message, byte[] pattern) -> {
            String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            try {
                JsonNode payload = objectMapper.readTree(body);
                dispatch(service, channel, payload);
            } catch (Exception e) {
                log.error("Error handling pub/sub message on {} ----> {}", channel, e.getMessage());
            }
        };

        container.addMessageListener(listener, ChannelTopic.of(PubSubChannels.RIDE_ACCEPTED));
        container.addMessageListener(listener, ChannelTopic.of(PubSubChannels.RIDE_PICKUP));
        container.addMessageListener(listener, ChannelTopic.of(PubSubChannels.RIDE_START));
        container.addMessageListener(listener, ChannelTopic.of(PubSubChannels.RIDE_DROPOFF));
        container.addMessageListener(listener, ChannelTopic.of(PubSubChannels.RIDE_PAYMENT));
        container.addMessageListener(listener, ChannelTopic.of(PubSubChannels.RIDE_ENDTRIP));
        container.addMessageListener(listener, ChannelTopic.of(PubSubChannels.RIDE_UPDATE_VEHICLETYPE));
        container.addMessageListener(listener, ChannelTopic.of(PubSubChannels.RIDE_CANCELBY_USER));
        container.addMessageListener(listener, ChannelTopic.of(PubSubChannels.RIDE_CANCELBY_DRIVER));
        container.addMessageListener(listener, ChannelTopic.of(PubSubChannels.RIDE_FETCHBY_USER));
        container.addMessageListener(listener, ChannelTopic.of(PubSubChannels.RIDE_IGNORE_DRIVER));
        container.addMessageListener(listener, ChannelTopic.of(PubSubChannels.RIDE_SUDDEN_END));
        container.addMessageListener(listener, ChannelTopic.of(PubSubChannels.DRIVER_GPS_UPDATE));

        return container;
    }

    private void dispatch(RideLifecycleService service, String channel, JsonNode payload) {
        switch (channel) {
            case PubSubChannels.RIDE_ACCEPTED -> service.acceptRide(payload);
            case PubSubChannels.RIDE_PICKUP -> service.pickupRide(payload);
            case PubSubChannels.RIDE_START -> service.startRide(payload);
            case PubSubChannels.RIDE_DROPOFF -> service.dropoffRide(payload);
            case PubSubChannels.RIDE_PAYMENT -> service.rideCompletePayment(payload);
            case PubSubChannels.RIDE_ENDTRIP -> service.endRide(payload);
            case PubSubChannels.RIDE_UPDATE_VEHICLETYPE -> service.updateVehicleType(payload);
            case PubSubChannels.RIDE_CANCELBY_USER -> service.cancelRideByUser(payload);
            case PubSubChannels.RIDE_CANCELBY_DRIVER -> service.rideCancelByDriver(payload);
            case PubSubChannels.RIDE_FETCHBY_USER -> service.fetchRide(payload);
            case PubSubChannels.RIDE_IGNORE_DRIVER -> service.updateIgnoredDriver(payload);
            case PubSubChannels.RIDE_SUDDEN_END -> service.suddenendByUser(payload);
            case PubSubChannels.DRIVER_GPS_UPDATE -> service.addGPSPoint(payload);
            default -> log.warn("Unhandled pub/sub channel {}", channel);
        }
    }
}
