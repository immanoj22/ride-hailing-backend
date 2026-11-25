package com.ridehail.driverservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotBlank;

/** Equivalent of validations/driver.validation.js's addRefSchema. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateReferralRequest {

    @NotBlank
    private String referralCode;

    private String driverId;

    public String getReferralCode() {
        return referralCode;
    }

    public void setReferralCode(String referralCode) {
        this.referralCode = referralCode;
    }

    public String getDriverId() {
        return driverId;
    }

    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }
}
