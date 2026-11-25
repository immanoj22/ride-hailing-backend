package com.ridehail.rideservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** validations/vehiclevalidation.js updateVehicleVariantSchema (multipart body + optional imageFile). */
@Data
public class UpdateVehicleVariantRequest {

    @NotBlank
    private String _id;

    private String vehicleTypeId;

    @Size(max = 100)
    private String type;

    @Size(max = 150)
    private String label;

    @Size(max = 1000)
    private String description;

    private Boolean isLocal;

    private Boolean isOutstation;

    private String localPrice;

    private String outstationPrice;

    private Integer seats;

    private Boolean delete;
}
