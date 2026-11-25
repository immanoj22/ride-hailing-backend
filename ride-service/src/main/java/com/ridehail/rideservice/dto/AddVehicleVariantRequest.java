package com.ridehail.rideservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** validations/vehiclevalidation.js addVehicleVariantSchema (multipart body + imageFile). */
@Data
public class AddVehicleVariantRequest {

    @NotNull
    private String vehicleTypeId;

    @NotBlank
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
}
