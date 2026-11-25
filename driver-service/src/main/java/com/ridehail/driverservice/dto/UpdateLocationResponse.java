package com.ridehail.driverservice.dto;

public class UpdateLocationResponse {
    private Double lat;
    private Double lng;

    public UpdateLocationResponse(Double lat, Double lng) {
        this.lat = lat;
        this.lng = lng;
    }

    public Double getLat() {
        return lat;
    }

    public Double getLng() {
        return lng;
    }
}
