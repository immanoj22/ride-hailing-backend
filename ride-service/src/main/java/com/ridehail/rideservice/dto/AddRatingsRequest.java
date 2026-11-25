package com.ridehail.rideservice.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Mirrors validations/ridevalidation.js addRatingsSchema (body). Field names kept verbatim. */
@Getter
@Setter
public class AddRatingsRequest {

    @NotNull
    private BigDecimal starrating;

    /** Joi rule commented out in the original - kept optional. */
    private String reason;

    @NotBlank
    private String userid;

    @NotBlank
    private String driverid;

    @NotBlank
    private String from;

    @NotBlank
    private String rideid;
}
