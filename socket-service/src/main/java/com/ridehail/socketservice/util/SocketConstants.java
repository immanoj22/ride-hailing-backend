package com.ridehail.socketservice.util;

/**
 * Mirrors helper/socket.constants.js (SOCKET_KEYS / CHAT_KEY) - event names and room helpers used
 * by the embedded netty-socketio server. Values MUST stay byte-identical to the Node originals so
 * existing mobile/web clients need no change.
 */
public final class SocketConstants {

    private SocketConstants() {
    }

    // room helpers (SOCKET_KEYS.personal_room / usertype_room)
    public static String personalRoom(String id) {
        return "user:" + id;
    }

    public static String userTypeRoom(String type) {
        return type + "s";
    }

    // CHAT_KEY
    public static final String CHAT_SEND = "chat:send";
    public static final String CHAT_RECEIVE = "chat:receive";
    public static final String CHAT_USER_SEND = "chat:user:send";
    public static final String CHAT_USER_RECEIVE = "chat:user:receive";
    public static final String DRIVER_LOCATION = "driver:location";
    public static final String DRIVER_LOCATION_UPDATE = "driver:location:update";

    // SOCKET_KEYS
    public static final String DISCONNECT = "disconnect";
    public static final String UPDATE_DRIVER_LOCATION = "update-driver-location";
    public static final String DRIVER_LOCATION_ACK = "driver-location-update-ack";
    public static final String NEAR_DRIVERS_RESP = "near-drivers-response";
    public static final String FIND_NEARBY_DRIVERS = "findNearbyDrivers";
    public static final String RIDE_REQ_ACCEPTED = "ride:req:accepted";
    public static final String RIDE_UPDATE_PICKUP = "ride:update:pickup";
    public static final String RIDE_UPDATE_START = "ride:update:start";
    public static final String RIDE_UPDATE_DROPOFF = "ride:update:dropoff";
    public static final String RIDE_UPDATE_PAYMENT = "ride:update:payment";
    public static final String RIDE_UPDATE_ENDTRIP = "ride:update:endtrip";
    public static final String RIDE_UPDATE_VEHICLETYPE = "ride:update:vehicletype";
    public static final String RIDE_CANCELBY_USER = "ride:cancelby:user";
    public static final String RIDE_CANCELBY_DRIVER = "ride:cancelby:driver";
    public static final String RIDE_FETCH_BY_USER = "ride:fetch:user";
    public static final String RIDE_IGNORE_BY_DRIVER = "ride:ignore:driver";
    public static final String RIDE_SUDDEN_END = "ride:sudden:end";
    public static final String OUTSTATION_ACCEPTED = "outstationAccepted";
    public static final String DRIVER_GPS_POINT = "driver:gps:point";
    public static final String NO_DRIVER = "nodriver";
    public static final String NEW_SUPPORT_MESSAGE = "new-support-message";
    public static final String TESTING = "Testing";
    public static final String CONFIRM_RIDE = "confirm_ride";

    // client store keys
    public static final String STORE_USER_ID = "userId";
    public static final String STORE_USER_TYPE = "userType";
    public static final String STORE_USER_DATA = "userData";
}
