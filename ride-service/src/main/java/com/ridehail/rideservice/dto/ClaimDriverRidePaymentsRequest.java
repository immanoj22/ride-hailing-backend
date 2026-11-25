package com.ridehail.rideservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Port of ridevalidation.js claimDriverRidePaymentsSchema.body. */
@Data
public class ClaimDriverRidePaymentsRequest {

    @NotNull
    private String driverId;
}
