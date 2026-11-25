package com.ridehail.rideservice.dto;

import java.math.BigDecimal;

import lombok.Data;

/** Body of ride.controller.js update_driver_fetch_config (all fields optional / partial update). */
@Data
public class UpdateDriverFetchConfigRequest {

    private BigDecimal withinKm;

    private Integer driverCount;

    private Integer requestPerDriver;

    private BigDecimal kmRestriction;
}
