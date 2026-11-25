package com.ridehail.userservice.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /update-fcm. Mirrors uservalidation.js's updateFcmSchema. */
public class UpdateFcmRequest {

    @NotBlank
    private String fcm;

    public String getFcm() {
        return fcm;
    }

    public void setFcm(String fcm) {
        this.fcm = fcm;
    }
}
