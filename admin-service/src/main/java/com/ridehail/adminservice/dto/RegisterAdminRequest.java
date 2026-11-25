package com.ridehail.adminservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Joi {@code registerSchema} (validations/admin.validation.js). {@code profile} is an optional
 * string URL here - the original multipart image upload to S3 is not ported in this service
 * (no S3 wiring); callers pass an already-uploaded URL.
 */
@Getter
@Setter
public class RegisterAdminRequest {

    @NotBlank
    private String fullname;

    @NotBlank
    @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    private String email;

    @NotBlank
    @Size(min = 8)
    @Pattern(regexp = "^[A-Za-z]+@[0-9]{2}$")
    private String password;

    @NotBlank
    @Pattern(regexp = "^[0-9]{10}$")
    private String mobileno;

    private String profile;
}
