package com.ridehail.driverservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotBlank;

/** Equivalent of validations/driver.validation.js's updateDriverFcmSchema. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateDriverFcmRequest {

    private String driverId;

    @NotBlank
    private String fcmToken;

    public String getDriverId() {
        return driverId;
    }

    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public void setFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }
}
