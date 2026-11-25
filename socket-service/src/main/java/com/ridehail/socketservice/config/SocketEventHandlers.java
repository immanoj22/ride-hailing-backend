package com.ridehail.socketservice.config;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehail.socketservice.service.DriverLocationService;
import com.ridehail.socketservice.service.SocketChatBridgeService;
import com.ridehail.socketservice.service.SocketPublisherService;
import com.ridehail.socketservice.util.SocketConstants;

/**
 * Port of config/socketListener.js -> registerEvents(): connect/disconnect handlers plus one
 * listener per client-emitted event. Ride/driver events republish to Redis via
 * {@link SocketPublisherService}; chat events persist via {@link SocketChatBridgeService}.
 */
@Component
public class SocketEventHandlers {

    private static final Logger log = LoggerFactory.getLogger(SocketEventHandlers.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final SocketPublisherService publisher;
    private final DriverLocationService driverLocationService;
    private final SocketChatBridgeService chatBridge;

    public SocketEventHandlers(SocketPublisherService publisher,
                               DriverLocationService driverLocationService,
                               SocketChatBridgeService chatBridge) {
        this.publisher = publisher;
        this.driverLocationService = driverLocationService;
        this.chatBridge = chatBridge;
    }

    public void register(SocketIOServer server) {

        server.addConnectListener(client -> {
            String userId = str(client.get(SocketConstants.STORE_USER_ID));
            String userType = str(client.get(SocketConstants.STORE_USER_TYPE));
            log.info("User connected: {} ({})", userId, userType);
            if (userId != null) {
                client.joinRoom(SocketConstants.personalRoom(userId));
            }
            if (userType != null) {
                client.joinRoom(SocketConstants.userTypeRoom(userType));
            }
        });

        server.addDisconnectListener(client ->
                log.info("User disconnected: {}", str(client.get(SocketConstants.STORE_USER_ID))));

        // driver:location -> push driver:location:update to user:<rideId>
        on(server, SocketConstants.DRIVER_LOCATION, (client, data) -> {
            Map<String, Object> m = asMap(data);
            Object rideId = m.get("rideId");
            if (rideId != null) {
                server.getRoomOperations(SocketConstants.personalRoom(String.valueOf(rideId)))
                        .sendEvent(SocketConstants.DRIVER_LOCATION_UPDATE, data);
            }
        });

        // chat:send (support chat)
        on(server, SocketConstants.CHAT_SEND, (client, data) -> {
            Map<String, Object> m = asMap(data);
            String from = str(m.get("from"));
            String msg = str(m.get("msg"));
            String userId = str(m.get("userId"));
            String ticketId = str(m.get("ticketId"));
            Object file = m.get("file");

            if (from == null || !(from.equals("admin") || from.equals("driver") || from.equals("user"))) {
                server.getBroadcastOperations().sendEvent(SocketConstants.CHAT_RECEIVE,
                        Map.of("success", false, "msg", "Error saving file to server."));
                return;
            }
            boolean isDirectChat = ticketId == null && userId != null;
            boolean isRideChat = ticketId != null;
            if (isDirectChat) {
                if (!from.equals("admin") && !from.equals("user")) {
                    server.getBroadcastOperations().sendEvent(SocketConstants.CHAT_RECEIVE,
                            Map.of("success", false, "msg", "Error saving file to server."));
                    return;
                }
            } else if (isRideChat) {
                if (from.equals("user") && userId == null) {
                    server.getBroadcastOperations().sendEvent(SocketConstants.CHAT_RECEIVE,
                            Map.of("success", false, "msg", "userId required when user chats inside a ride"));
                    return;
                }
            } else {
                server.getBroadcastOperations().sendEvent(SocketConstants.CHAT_RECEIVE,
                        Map.of("success", false, "msg", "Invalid chat data combination"));
                return;
            }
            if (msg == null && file == null) {
                server.getBroadcastOperations().sendEvent(SocketConstants.CHAT_RECEIVE,
                        Map.of("success", false, "msg", "Message or file is required"));
                return;
            }

            var result = chatBridge.saveSupportMessage(ticketId, userId, msg, from, null);
            if (result == null || !result.path("status").asBoolean(false)) {
                return;
            }
            Object payload = objectMapper.convertValue(result.path("data"), Object.class);
            if (userId != null) {
                server.getRoomOperations(SocketConstants.personalRoom(userId))
                        .sendEvent(SocketConstants.NEW_SUPPORT_MESSAGE, payload);
            }
            server.getRoomOperations(SocketConstants.userTypeRoom("admin"))
                    .sendEvent(SocketConstants.NEW_SUPPORT_MESSAGE, payload);
        });

        // chat:user:send (ride chat)
        on(server, SocketConstants.CHAT_USER_SEND, (client, data) -> {
            Map<String, Object> m = asMap(data);
            String rideId = str(m.get("rideId"));
            String senderId = str(m.get("senderId"));
            String from = str(m.get("from"));
            String msg = str(m.get("msg"));
            Object file = m.get("file");

            if (rideId == null) {
                client.sendEvent(SocketConstants.CHAT_USER_RECEIVE, Map.of("success", false, "msg", "rideId is required"));
                return;
            }
            if (senderId == null) {
                client.sendEvent(SocketConstants.CHAT_USER_RECEIVE, Map.of("success", false, "msg", "senderId is required"));
                return;
            }
            if (from == null || !(from.equals("user") || from.equals("driver"))) {
                client.sendEvent(SocketConstants.CHAT_USER_RECEIVE, Map.of("success", false, "msg", "Invalid sender"));
                return;
            }
            if (msg == null && file == null) {
                client.sendEvent(SocketConstants.CHAT_USER_RECEIVE, Map.of("success", false, "msg", "Message or file is required"));
                return;
            }
            var result = chatBridge.saveRideChat(rideId, senderId, from, msg);
            if (result == null || !result.path("status").asBoolean(false)) {
                client.sendEvent(SocketConstants.CHAT_USER_RECEIVE,
                        Map.of("success", false, "msg", result == null ? "" : result.path("message").asText("")));
            }
        });

        // update-driver-location
        on(server, SocketConstants.UPDATE_DRIVER_LOCATION, (client, data) -> {
            String userType = str(client.get(SocketConstants.STORE_USER_TYPE));
            String userId = str(client.get(SocketConstants.STORE_USER_ID));
            Map<String, Object> m = asMap(data);
            Object lat = m.get("lat");
            Object lng = m.get("lng");
            if (lat == null || lng == null || !"driver".equals(userType)) {
                return;
            }
            driverLocationService.updateDriverLocation(userId, toDouble(lat), toDouble(lng));
            publisher.onUpdateDriverLocation(data, userId);
        });

        // findNearbyDrivers
        on(server, SocketConstants.FIND_NEARBY_DRIVERS, (client, data) -> {
            String userId = str(client.get(SocketConstants.STORE_USER_ID));
            Map<String, Object> m = asMap(data);
            var resp = driverLocationService.findNearbyDrivers(toDouble(m.get("lat")), toDouble(m.get("lng")));
            if (userId != null) {
                server.getRoomOperations(SocketConstants.personalRoom(userId))
                        .sendEvent(SocketConstants.NEAR_DRIVERS_RESP, resp);
            }
        });

        // ride/driver events -> Redis pub/sub
        onPublish(server, SocketConstants.RIDE_REQ_ACCEPTED, publisher::onRideAccept);
        onPublish(server, SocketConstants.RIDE_UPDATE_PICKUP, publisher::onRidePickup);
        onPublish(server, SocketConstants.RIDE_UPDATE_START, publisher::onRideStart);
        onPublish(server, SocketConstants.RIDE_UPDATE_DROPOFF, publisher::onRideDropoff);
        onPublish(server, SocketConstants.RIDE_UPDATE_PAYMENT, publisher::onRidePayment);
        onPublish(server, SocketConstants.RIDE_UPDATE_ENDTRIP, publisher::onRideEnd);
        onPublish(server, SocketConstants.RIDE_UPDATE_VEHICLETYPE, publisher::onRideUpdateVehicleType);
        onPublish(server, SocketConstants.RIDE_CANCELBY_USER, publisher::onRideCancelByUser);
        onPublish(server, SocketConstants.RIDE_CANCELBY_DRIVER, publisher::onRideCancelByDriver);
        onPublish(server, SocketConstants.RIDE_FETCH_BY_USER, publisher::onRideFetchByUser);
        onPublish(server, SocketConstants.RIDE_IGNORE_BY_DRIVER, publisher::onRideIgnoreByDriver);
        onPublish(server, SocketConstants.RIDE_SUDDEN_END, publisher::onRideSuddenEnd);
        onPublish(server, SocketConstants.DRIVER_GPS_POINT, publisher::onUpdateDriverGPS);

        // outstationAccepted / confirm_ride - Node only logs
        on(server, SocketConstants.OUTSTATION_ACCEPTED, (client, data) -> log.info("outstationAccepted {}", data));
        on(server, SocketConstants.CONFIRM_RIDE, (client, data) -> log.info("confirm_ride {}", data));

        // Testing
        on(server, SocketConstants.TESTING, (client, data) ->
                server.getRoomOperations("users").sendEvent(SocketConstants.NO_DRIVER, "Noderiver preserntedd"));
    }

    private interface Handler {
        void handle(SocketIOClient client, Object data) throws Exception;
    }

    private void on(SocketIOServer server, String event, Handler handler) {
        server.addEventListener(event, Object.class, (client, data, ack) -> {
            try {
                handler.handle(client, data);
            } catch (Exception e) {
                log.error("Error handling socket event {}", event, e);
            }
        });
    }

    private void onPublish(SocketIOServer server, String event, java.util.function.BiConsumer<Object, Object> pub) {
        on(server, event, (client, data) -> pub.accept(data, client.get(SocketConstants.STORE_USER_DATA)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object data) {
        if (data instanceof String s) {
            try {
                return objectMapper.readValue(s, Map.class);
            } catch (Exception e) {
                return Map.of();
            }
        }
        if (data instanceof Map) {
            return (Map<String, Object>) data;
        }
        return objectMapper.convertValue(data, Map.class);
    }

    private String str(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o);
        return s.isEmpty() || "null".equals(s) || "undefined".equals(s) ? null : s;
    }

    private double toDouble(Object o) {
        if (o == null) {
            return 0d;
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0d;
        }
    }
}
