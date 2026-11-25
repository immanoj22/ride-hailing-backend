package com.ridehail.commonservice.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public class GetRouteDetailsRequest {

    @NotNull
    @Valid
    private LocationDto userLocation;

    @NotNull
    @Valid
    private LocationDto destination;

    private String from = "user";

    private Boolean desiredLocation = false;

    private List<LocationDto> multi;

    private Boolean isTwoWay = false;

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

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public Boolean getDesiredLocation() {
        return desiredLocation;
    }

    public void setDesiredLocation(Boolean desiredLocation) {
        this.desiredLocation = desiredLocation;
    }

    public List<LocationDto> getMulti() {
        return multi;
    }

    public void setMulti(List<LocationDto> multi) {
        this.multi = multi;
    }

    public Boolean getIsTwoWay() {
        return isTwoWay;
    }

    public void setIsTwoWay(Boolean isTwoWay) {
        this.isTwoWay = isTwoWay;
    }
}
