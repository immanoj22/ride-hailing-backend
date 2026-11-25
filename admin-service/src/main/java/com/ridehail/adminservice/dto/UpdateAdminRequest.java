package com.ridehail.adminservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Joi {@code updateAdminSchema} (validations/admin.validation.js). */
@Getter
@Setter
public class UpdateAdminRequest {

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
