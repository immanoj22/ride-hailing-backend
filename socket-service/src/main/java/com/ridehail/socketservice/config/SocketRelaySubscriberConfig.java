package com.ridehail.socketservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.corundumstudio.socketio.SocketIOServer;
import com.ridehail.common.realtime.PubSubChannels;

/**
 * Subscribes to {@link PubSubChannels#SOCKET_RELAY} (= {@code ridehail:socket:relay}) - the single
 * server->client push channel that replaces {@code @socket.io/redis-emitter}. Each message is a
 * {@code {room, event, data}} envelope; {@code room == null} broadcasts, otherwise it targets
 * that room. Mirrors config/socketemitter.js semantics.
 */
@Configuration
public class SocketRelaySubscriberConfig {

    private static final Logger log = LoggerFactory.getLogger(SocketRelaySubscriberConfig.class);

    @Bean
    public SocketRelayListener socketRelayListener(SocketIOServer server) {
        return new SocketRelayListener(server);
    }

    @Bean
    public RedisMessageListenerContainer socketRelayContainer(
            RedisConnectionFactory connectionFactory,
            SocketRelayListener listener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        MessageListenerAdapter adapter = new MessageListenerAdapter(listener, "onMessage");
        container.addMessageListener(adapter, new ChannelTopic(PubSubChannels.SOCKET_RELAY));
        return container;
    }

    public static class SocketRelayListener {
        private final SocketIOServer server;
        private final ObjectMapper objectMapper = new ObjectMapper();

        public SocketRelayListener(SocketIOServer server) {
            this.server = server;
        }

        public void onMessage(String message) {
            try {
                JsonNode envelope = objectMapper.readTree(message);
                String room = envelope.hasNonNull("room") ? envelope.get("room").asText() : null;
                String event = envelope.path("event").asText();
                Object data = objectMapper.convertValue(envelope.path("data"), Object.class);
                if (room == null) {
                    server.getBroadcastOperations().sendEvent(event, data);
                } else {
                    server.getRoomOperations(room).sendEvent(event, data);
                }
            } catch (Exception e) {
                log.error("Failed to relay socket message: {}", message, e);
            }
        }
    }
}
