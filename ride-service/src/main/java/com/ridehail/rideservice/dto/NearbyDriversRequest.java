package com.ridehail.rideservice.dto;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * ride.controller.js nearby_driversAvailable reads pickup{lat,lng}, vehicleTypeId,
 * vehicleVariantId, driverSkip[], radiusMeters, km, time off req.body (no Joi schema on this route).
 */
@Data
public class NearbyDriversRequest {

    @NotNull
    private JsonNode pickup;

    private String vehicleTypeId;

    private String vehicleVariantId;

    private List<String> driverSkip;

    private Double radiusMeters = 5000d;

    private Double km;

    private Double time;
}
