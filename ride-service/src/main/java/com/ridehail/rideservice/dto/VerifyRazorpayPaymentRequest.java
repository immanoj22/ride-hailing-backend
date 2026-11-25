package com.ridehail.rideservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Port of ridevalidation.js verifyRazorpayPaymentSchema.body. */
@Data
public class VerifyRazorpayPaymentRequest {

    @NotBlank
    @JsonProperty("razorpay_order_id")
    private String razorpayOrderId;

    @NotBlank
    @JsonProperty("razorpay_payment_id")
    private String razorpayPaymentId;

    @NotBlank
    @JsonProperty("razorpay_signature")
    private String razorpaySignature;
}
