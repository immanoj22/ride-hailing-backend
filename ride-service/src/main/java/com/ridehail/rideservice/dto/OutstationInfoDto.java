package com.ridehail.rideservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * ridevalidation.js outstationRideSchema.body.outstation - only tripType is Joi-validated
 * ("oneway" | "round" | "rental"). ride.controller.js additionally reads kmEstimated /
 * durationEstimated / packageId off this object.
 */
@Data
public class OutstationInfoDto {

    @NotBlank
    private String tripType;

    private Double kmEstimated;

    private Double durationEstimated;

    private String packageId;
}
