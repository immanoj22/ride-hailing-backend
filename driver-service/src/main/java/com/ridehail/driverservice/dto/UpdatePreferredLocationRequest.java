package com.ridehail.driverservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotNull;

/** Equivalent of validations/driver.validation.js's updatePreferredLocationSchema. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdatePreferredLocationRequest {

    @NotNull
    private JsonNode preferredLocations;

    public JsonNode getPreferredLocations() {
        return preferredLocations;
    }

    public void setPreferredLocations(JsonNode preferredLocations) {
        this.preferredLocations = preferredLocations;
    }
}
