package com.ridehail.commonservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public class GetEstimatedTimeRequest {

    @NotNull
    @Valid
    private LocationDto userLocation;

    @NotNull
    @Valid
    private LocationDto destination;

    private Boolean desiredLocation;

    private String from;

    public LocationDto getUserLocation() {
        return userLocation;
    }

    public void setUserLocation(LocationDto userLocation) {
        this.userLocation = userLocation;
    }

    public LocationDto getDestination() {
        return destination;
    }

    public void setDestination(LocationDto destination) {
        this.destination = destination;
    }

    public Boolean getDesiredLocation() {
        return desiredLocation;
    }

    public void setDesiredLocation(Boolean desiredLocation) {
        this.desiredLocation = desiredLocation;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }
}
