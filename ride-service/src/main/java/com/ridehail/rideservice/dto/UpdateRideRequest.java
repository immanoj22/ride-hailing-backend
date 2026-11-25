package com.ridehail.rideservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Data;

/** Port of ridevalidation.js updateRideSchema.body (createRideSchema + required _id). */
@Data
@EqualsAndHashCode(callSuper = true)
public class UpdateRideRequest extends CreateRideRequest {

    @NotNull
    private String _id;
}
