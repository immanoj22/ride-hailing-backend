package com.ridehail.rideservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Port of ridevalidation.js outstationFeedSchema.body (plus getCount/tripType read off req.body). */
@Data
public class OutstationFeedRequest {

    @NotBlank
    private String driverId;

    private Boolean seeAll = false;

    private String selectedCity;

    private Integer page = 1;

    private Integer limit = 10;

    private Boolean getCount = false;

    private String tripType;
}
