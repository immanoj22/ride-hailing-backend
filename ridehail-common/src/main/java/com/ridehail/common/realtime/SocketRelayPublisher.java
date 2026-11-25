package com.ridehail.common.realtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Equivalent of config/socketemitter.js's {@code SocketEmitter} (backed there by
 * {@code @socket.io/redis-emitter}). Every business service publishes a small JSON envelope to
 * {@link PubSubChannels#SOCKET_RELAY}; only socket-service subscribes to it and relays into its
 * embedded netty-socketio server. Room names ({@code user:<id>}, {@code driver:<id>},
 * {@code users}, {@code drivers}) are unchanged from the original so client-facing behaviour is
 * identical - only the internal Redis wire format differs (plain JSON here vs the
 * socket.io-redis-adapter msgpack protocol there), which no client ever sees.
 */
@Component
public class SocketRelayPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SocketRelayPublisher(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void toUser(String userId, String event, Object data) {
        publish("user:" + userId, event, data);
    }

    public void toDriver(String driverId, String event, Object data) {
        publish("driver:" + driverId, event, data);
    }

    public void toDrivers(String event, Object data) {
        publish("drivers", event, data);
    }

    public void toUsers(String event, Object data) {
        publish("users", event, data);
    }

    public void toAdmins(String event, Object data) {
        publish("admins", event, data);
    }

    public void broadcast(String event, Object data) {
        publish(null, event, data);
    }

    public void toMultiple(String userType, List<String> ids, String event, Object data) {
        for (String id : ids) {
            publish(userType + ":" + id, event, data);
        }
    }

    private void publish(String room, String event, Object data) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("room", room);
            envelope.put("event", event);
            envelope.put("data", data);
            redisTemplate.convertAndSend(PubSubChannels.SOCKET_RELAY, objectMapper.writeValueAsString(envelope));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish socket relay event", e);
        }
    }
}
