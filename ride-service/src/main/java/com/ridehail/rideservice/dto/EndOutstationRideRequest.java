package com.ridehail.rideservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** ride.controller.js end_outstation_ride reads rideId, driverId, waitingMinutes off req.body (no Joi schema). */
@Data
public class EndOutstationRideRequest {

    @NotBlank
    private String rideId;

    @NotBlank
    private String driverId;

    private Double waitingMinutes = 0d;
}
