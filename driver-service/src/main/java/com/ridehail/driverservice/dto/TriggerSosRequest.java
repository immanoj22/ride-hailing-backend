package com.ridehail.driverservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TriggerSosRequest {

    private String rideId;
    private LatLngRequest location;

    public String getRideId() {
        return rideId;
    }

    public void setRideId(String rideId) {
        this.rideId = rideId;
    }

    public LatLngRequest getLocation() {
        return location;
    }

    public void setLocation(LatLngRequest location) {
        this.location = location;
    }
}
