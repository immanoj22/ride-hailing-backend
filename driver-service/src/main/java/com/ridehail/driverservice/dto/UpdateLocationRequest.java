package com.ridehail.driverservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotNull;

/** Equivalent of validations/driver.validation.js's locationUpdateSchema. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateLocationRequest {

    @NotNull
    private Double lat;

    @NotNull
    private Double lng;

    private String driverId;

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

    public String getDriverId() {
        return driverId;
    }

    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }
}
