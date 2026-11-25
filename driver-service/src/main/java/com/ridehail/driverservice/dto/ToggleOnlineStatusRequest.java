package com.ridehail.driverservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;

/** Equivalent of validations/driver.validation.js's toggleOnlineSchema. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToggleOnlineStatusRequest {

    @NotBlank
    private String driverStatus;

    /** GeoJSON Point {type,coordinates:[lng,lat]}, kept loose since the legacy schema is untyped. */
    private JsonNode currentLocation;

    public String getDriverStatus() {
        return driverStatus;
    }

    public void setDriverStatus(String driverStatus) {
        this.driverStatus = driverStatus;
    }

    public JsonNode getCurrentLocation() {
        return currentLocation;
    }

    public void setCurrentLocation(JsonNode currentLocation) {
        this.currentLocation = currentLocation;
    }
}
