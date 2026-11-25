package com.ridehail.userservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code coordinate: { lat, lng }} shape shared by validations/uservalidation.js's
 * addAddressSchema/updateAddressSchema.
 */
@Getter
@Setter
public class CoordinateDto {

    @NotNull
    private Double lat;

    @NotNull
    private Double lng;
}
