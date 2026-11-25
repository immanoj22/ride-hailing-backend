package com.ridehail.rideservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** validations/vehiclevalidation.js addVehicleTypeSchema (multipart body + imageFile). */
@Data
public class AddVehicleTypeRequest {

    @NotBlank
    private String type;

    @NotBlank
    @Size(min = 2, max = 50)
    private String label;

    /** Joi: .allow("", null).required() */
    private String description;
}
