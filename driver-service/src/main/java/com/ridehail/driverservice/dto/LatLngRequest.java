package com.ridehail.driverservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Reusable {lat, lng} pair - equivalent of the many ad-hoc {lat,lng} object literals in the
 * original controller (pickup, currentLocation, sos location, ...). */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LatLngRequest {

    private Double lat;
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
