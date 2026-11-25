package com.ridehail.rideservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** validations/vehiclevalidation.js updateVehicleTypeSchema (multipart body + optional imageFile). */
@Data
public class UpdateVehicleTypeRequest {

    @NotBlank
    private String _id;

    private String type;

    @Size(min = 2, max = 50)
    private String label;

    private String description;

    /** Joi boolean optional. */
    private Boolean delete;
}
