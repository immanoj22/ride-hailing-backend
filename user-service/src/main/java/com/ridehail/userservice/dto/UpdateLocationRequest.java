package com.ridehail.userservice.dto;

import jakarta.validation.constraints.NotNull;

/** POST /update-location. Mirrors uservalidation.js's updateLocationSchema. */
public class UpdateLocationRequest {

    @NotNull
    private Double lat;

    @NotNull
    private Double lng;

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLng() {
        return lng;
    }

    public void setLng(Double lng) {
        this.lng = lng;
    }
}
