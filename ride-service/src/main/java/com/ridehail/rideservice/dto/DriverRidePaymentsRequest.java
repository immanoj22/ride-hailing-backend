package com.ridehail.rideservice.dto;

import lombok.Data;

/** Body of ride.controller.js getDriverRidePayments (no Joi schema on the route). */
@Data
public class DriverRidePaymentsRequest {

    private String driverId;

    /** Only applied when explicitly a boolean in the original. */
    private Boolean isClaimed;

    private Integer page;

    private Integer limit;
}
