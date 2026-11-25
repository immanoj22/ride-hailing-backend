package com.ridehail.socketservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.corundumstudio.socketio.SocketIOServer;
import com.ridehail.socketservice.security.SocketHandshakeAuthListener;

/**
 * Boots the embedded netty-socketio protocol server (replaces server.js's
 * {@code new Server(httpServer, ...)}). It binds its own port ({@code ridehail.socketio.port},
 * default 8001) since Tomcat owns {@code server.port}; path {@code /socket.io}, CORS origin
 * {@code *}, websocket + polling transports - identical to the Node config.
 */
@Configuration
public class SocketIoServerConfig {

    private static final Logger log = LoggerFactory.getLogger(SocketIoServerConfig.class);

    @Bean
    public SocketIOServer socketIOServer(
            @Value("${ridehail.socketio.port:8001}") int port,
            SocketHandshakeAuthListener authListener) {
        com.corundumstudio.socketio.Configuration config = new com.corundumstudio.socketio.Configuration();
        config.setHostname("0.0.0.0");
        config.setPort(port);
        config.setContext("/socket.io");
        config.setOrigin("*");
        config.setAuthorizationListener(authListener);
        config.setRandomSession(true);
        return new SocketIOServer(config);
    }

    @Bean
    public SmartLifecycle socketIOServerLifecycle(SocketIOServer server, SocketEventHandlers handlers) {
        return new SmartLifecycle() {
            private volatile boolean running = false;

            @Override
            public void start() {
                handlers.register(server);
                server.start();
                running = true;
                log.info("netty-socketio server started on port {}", server.getConfiguration().getPort());
            }

            @Override
            public void stop() {
                server.stop();
                running = false;
            }

            @Override
            public boolean isRunning() {
                return running;
            }

            @Override
            public int getPhase() {
                return Integer.MAX_VALUE - 1;
            }
        };
    }
}
