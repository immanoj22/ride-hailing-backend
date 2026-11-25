package com.ridehail.rideservice.dto;

import lombok.Data;

/** Body of ride.controller.js getReferralDriverRidePayments (no Joi schema on the route). */
@Data
public class ReferralDriverRidePaymentsRequest {

    private String refDriverId;

    private String ownerDriverId;
}
