package com.ridehail.rideservice.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Port of ridevalidation.js addGPSPointSchema.body. The Joi schema only validates
 * rideId/driverId/speed/heading/accuracy; ride.controller.js additionally reads a
 * {@code coordinates} array shaped [longitude, latitude] (GeoJSON order).
 */
@Data
public class AddGpsPointRequest {

    @NotBlank
    private String rideId;

    @NotBlank
    private String driverId;

    /** [longitude, latitude]. */
    private List<Double> coordinates;

    private Double speed;

    private Double heading;

    private Double accuracy;
}
