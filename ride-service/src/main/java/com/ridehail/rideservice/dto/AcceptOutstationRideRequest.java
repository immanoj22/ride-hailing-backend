package com.ridehail.rideservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Port of ridevalidation.js acceptOutstationRideSchema.body. */
@Data
public class AcceptOutstationRideRequest {

    @NotBlank
    private String rideId;

    /** ride.controller.js falls back to req.body.driverId when req.driver is absent. */
    private String driverId;
}
