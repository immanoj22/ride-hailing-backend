package com.ridehail.rideservice.dto;

import lombok.Data;

/** Body of ride.controller.js getDriverReferralEarnings (no Joi schema on the route). */
@Data
public class DriverIdRequest {

    private String driverId;
}
