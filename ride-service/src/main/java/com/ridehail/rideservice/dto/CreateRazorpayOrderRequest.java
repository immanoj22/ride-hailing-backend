package com.ridehail.rideservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** Port of ridevalidation.js createRazorpayOrderSchema.body. */
@Data
public class CreateRazorpayOrderRequest {

    @NotNull
    @Positive
    private Double amount;

    /** Joi default "INR", uppercased. */
    private String currency = "INR";
}
