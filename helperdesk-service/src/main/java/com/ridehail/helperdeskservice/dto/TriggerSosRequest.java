package com.ridehail.helperdeskservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** POST /sos/trigger. Mirrors validations/helperdesk.validation.js's triggerSOS. */
public class TriggerSosRequest {

    @NotBlank
    private String rideId;

    @NotBlank
    private String userId;

    @NotBlank
    @Pattern(regexp = "rider|driver")
    private String userType;

    @NotNull
    @Valid
    private LatLng location;

    public String getRideId() {
        return rideId;
    }

    public void setRideId(String rideId) {
        this.rideId = rideId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public LatLng getLocation() {
        return location;
    }

    public void setLocation(LatLng location) {
        this.location = location;
    }

    public static class LatLng {
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
}
