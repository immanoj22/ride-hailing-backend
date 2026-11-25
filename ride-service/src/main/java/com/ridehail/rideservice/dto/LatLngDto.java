package com.ridehail.rideservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** ridevalidation.js pickup/drop: { lat: Joi.number().required(), lng: Joi.number().required() }. */
@Data
public class LatLngDto {

    @NotNull
    private Double lat;

    @NotNull
    private Double lng;
}
