package com.ridehail.rideservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** ride.controller.js getRentalSlots reads vehicleVariantId off req.body (no Joi schema). */
@Data
public class RentalSlotsRequest {

    @NotBlank
    private String vehicleVariantId;
}
