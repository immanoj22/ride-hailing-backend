package com.ridehail.common.realtime;

/**
 * Exact channel names from socket-service/helper/socket.constants.js (PUBSUB_KEY). These carry
 * plain JSON (client socket event -> business logic in ride/driver/helperdesk-service) and MUST
 * keep these exact string values - other, not-yet-migrated instances or tooling may still
 * reference them during a staged rollout.
 */
public final class PubSubChannels {

    public static final String UPDATE_DRIVER_LOCATION = "pubsub:driver:location:update";
    public static final String RIDE_ACCEPTED = "pubsub:ride:accepted";
    public static final String RIDE_PICKUP = "pubsub:ride:update:pickup";
    public static final String RIDE_START = "pubsub:ride:update:start";
    public static final String RIDE_DROPOFF = "pubsub:ride:update:dropoff";
    public static final String RIDE_PAYMENT = "pubsub:ride:update:payment";
    public static final String RIDE_ENDTRIP = "pubsub:ride:update:endtrip";
    public static final String RIDE_UPDATE_VEHICLETYPE = "pubsub:ride:update:vehicletype";
    public static final String RIDE_CANCELBY_USER = "pubsub:ride:cancelby:user";
    public static final String RIDE_CANCELBY_DRIVER = "pubsub:ride:cancelby:driver";
    public static final String RIDE_FETCHBY_USER = "pubsub:ride:fetch:user";
    public static final String RIDE_IGNORE_DRIVER = "pubsub:ride:ignore:driver";
    public static final String RIDE_SUDDEN_END = "pubsub:ride:sudden:end";
    public static final String SOS_TRIGGERED = "pubsub:sos:triggered";
    public static final String DRIVER_STATUS = "pubsub:driver:status";
    public static final String DRIVER_GPS_UPDATE = "pubsub:driver:gps:update";
    public static final String CHAT_SEND = "pubsub:chat:send";

    /**
     * NEW (not in the legacy app): single channel carrying the server->client push envelope
     * {@code {room, event, data}} that replaces {@code @socket.io/redis-emitter} (whose wire
     * format is Socket.IO-adapter-specific msgpack, impractical to reproduce from plain Java).
     * Only socket-service's netty-socketio server subscribes to this; every other service
     * publishes to it via {@link SocketRelayPublisher} instead of emitting sockets directly.
     */
    public static final String SOCKET_RELAY = "ridehail:socket:relay";

    private PubSubChannels() {
    }
}
