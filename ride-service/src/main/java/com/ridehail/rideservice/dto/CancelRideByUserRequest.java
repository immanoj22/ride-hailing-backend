package com.ridehail.rideservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Port of ridevalidation.js cancelRideByUserSchema.body. */
@Data
public class CancelRideByUserRequest {

    @NotNull
    private String _id;

    @NotNull
    private String status;

    @NotNull
    private String reason;
}
