package com.ridehail.driverservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Flexible field-projection response reused by every "select only a few fields" driver listing
 * endpoint (getMultipleDriversById, get_profiles, nearby_drivers, nearby_outstation_drivers) -
 * unused fields are left {@code null} and dropped from the JSON output, matching each endpoint's
 * own Mongoose {@code select(...)} projection.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DriverProjectionResponse {

    @JsonProperty("_id")
    private String id;
    private String socketId;
    private String firstName;
    private String lastName;
    private String email;
    private String mobile;
    private String profileImage;
    private String vehicleNumber;
    private String fcmToken;
    private String vehicleType;
    private DriverResponse.GeoPointResponse currentLocation;
    private JsonNode preferredLocations;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSocketId() {
        return socketId;
    }

    public void setSocketId(String socketId) {
        this.socketId = socketId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getProfileImage() {
        return profileImage;
    }

    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public void setVehicleNumber(String vehicleNumber) {
        this.vehicleNumber = vehicleNumber;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public void setFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public DriverResponse.GeoPointResponse getCurrentLocation() {
        return currentLocation;
    }

    public void setCurrentLocation(DriverResponse.GeoPointResponse currentLocation) {
        this.currentLocation = currentLocation;
    }

    public JsonNode getPreferredLocations() {
        return preferredLocations;
    }

    public void setPreferredLocations(JsonNode preferredLocations) {
        this.preferredLocations = preferredLocations;
    }
}
