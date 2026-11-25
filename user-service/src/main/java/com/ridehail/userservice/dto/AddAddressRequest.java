package com.ridehail.userservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * POST /add-address body, mirrors validations/uservalidation.js's addAddressSchema exactly
 * (userId itself is never taken from the body - it comes from the authenticated principal, same
 * as the original controller reading {@code req.user._id}).
 */
@Getter
@Setter
public class AddAddressRequest {

    @NotBlank
    private String label;

    @NotBlank
    private String street1;

    /** Joi: {@code Joi.string().trim().allow("")} - optional, empty allowed. */
    private String street2;

    /** Joi: {@code Joi.string().trim().allow("")} - optional, empty allowed. */
    private String district;

    /** Joi: {@code Joi.string().trim().allow("")} - optional, empty allowed. */
    private String landmark;

    @NotBlank
    @Pattern(regexp = "home|work|other", message = "type must be one of [home, work, other]")
    private String type;

    @NotNull
    @Valid
    private CoordinateDto coordinate;
}
