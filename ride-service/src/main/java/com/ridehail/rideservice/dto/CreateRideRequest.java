package com.ridehail.rideservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Port of ridevalidation.js createRideSchema.body. */
@Data
public class CreateRideRequest {

    @NotNull
    @Valid
    private LatLngDto pickup;

    @NotNull
    @Valid
    private LatLngDto drop;

    @NotNull
    private String pickupAddress;

    @NotNull
    private String dropAddress;

    private String pickupDistrict;

    private String dropoffDistrict;

    private String rideType = "local";

    @NotNull
    private String vehicleTypeId;

    /** Joi: required when isBookAny is false. Enforced in the service. */
    private String vehicleVariantId;

    private String promo = "";

    @NotNull
    private Boolean isBookAny;

    /** Read from the payload by ride.controller.js even though not in the Joi schema. */
    private Double km;

    private Integer time;
}
