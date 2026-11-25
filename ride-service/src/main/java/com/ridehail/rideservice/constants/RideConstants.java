package com.ridehail.rideservice.constants;

import java.util.List;

/**
 * Verbatim string values from helpers/ride.constants.js (SOCKET_KEYS, RIDE_REQ_RES_KEY,
 * DRIVER_STATUS, ACTIVE_RIDE_STATUSES) plus the one PUBSUB_KEY constant that ridehail-common's
 * {@link com.ridehail.common.realtime.PubSubChannels} does not expose (UPDATE_DRIVER).
 */
public final class RideConstants {

    private RideConstants() {
    }

    // ride.constants.js PUBSUB_KEY.UPDATE_DRIVER - published by helpers/ride.publisher.js onUpdateDriver
    public static final String PUBSUB_UPDATE_DRIVER = "pubsub:driver:update";

    // ride.constants.js SOCKET_KEYS
    public static final String SK_RIDE_ACCEPT_RESPONSE = "ride:accept:response";
    public static final String SK_RIDE_REQ_TIMEOUT = "ride:req:timeout";
    public static final String SK_RIDE_GPS_POINT_UPDATE = "ride:gps:update:response";
    public static final String SK_RIDE_PICKUP_RESPONSE = "ride:update:pickup:response";
    public static final String SK_RIDE_START_RESPONSE = "ride:update:startride:response";
    public static final String SK_RIDE_DROPOFF_RESPONSE = "ride:update:dropoff:response";
    public static final String SK_RIDE_PAYMENT_RESPONSE = "ride:update:payment:response";
    public static final String SK_RIDE_ENDTRIP_RESPONSE = "ride:update:endtrip:response";
    public static final String SK_UPDATE_VEHICLETYPE = "ride:update:vehicletype:response";
    public static final String SK_NO_DRIVER = "ride:nodriver:response";
    public static final String SK_REQUEST_DRIVER_FOR_TRIP = "ride:requestdriverfortrip:response";
    public static final String SK_CANCEL_TRIP_BY_USER = "ride:cancel:tripby:user";
    public static final String SK_CANCEL_TRIP_BY_DRIVER_SEND_USER = "ride:cancel:tripby:driver";
    public static final String SK_CANCEL_TRIP_SUDDENLY = "ride:cancel:trip:suddenly";

    // ride.constants.js RIDE_REQ_RES_KEY
    public static final String RR_ANOTHER_ACCEPT = "another-accept";
    public static final String RR_RIDE_ACCEPT = "ride-accepted";
    public static final String RR_RIDE_ACCEPT_ERR = "ride-accepted-error";

    // ride.constants.js DRIVER_STATUS
    public static final String DRIVER_STATUS_RIDE_ACCEPT = "rideaccept";
    public static final String DRIVER_STATUS_RIDE_PICKUP = "pickup";
    public static final String DRIVER_STATUS_RIDE_START = "startride";
    public static final String DRIVER_STATUS_RIDE_END = "endtrip";

    public static final List<String> ACTIVE_RIDE_STATUSES = List.of(
            "create", "accept", "pickup", "start", "dropoff", "completepayment", "startride");

    public static final List<String> NON_CHARGEABLE_CANCEL_STATUSES = List.of(
            "create", "assigned", "updated", "timeout");

    public static final List<String> CANCEL_ALLOWED_STATUSES = List.of(
            "create", "accept", "pickup", "timeout", "updated", "assigned");
}
