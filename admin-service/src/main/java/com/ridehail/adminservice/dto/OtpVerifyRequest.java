package com.ridehail.adminservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** Joi {@code otpVerifySchema} (validations/admin.validation.js). */
@Getter
@Setter
public class OtpVerifyRequest {

    @jakarta.validation.constraints.NotBlank
    @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    private String email;

    @NotNull
    private Long otpVerify;
}
