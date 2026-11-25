package com.ridehail.driverservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** getDriverForNotification's {mobile, fcmToken} projection (Mongo still returns _id too). */
public class DriverNotificationTargetResponse {

    @JsonProperty("_id")
    private String id;
    private String mobile;
    private String fcmToken;

    public DriverNotificationTargetResponse(String id, String mobile, String fcmToken) {
        this.id = id;
        this.mobile = mobile;
        this.fcmToken = fcmToken;
    }

    public String getId() {
        return id;
    }

    public String getMobile() {
        return mobile;
    }

    public String getFcmToken() {
        return fcmToken;
    }
}
