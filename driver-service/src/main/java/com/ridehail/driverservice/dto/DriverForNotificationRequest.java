package com.ridehail.driverservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** {@code mobile} may be a single string or an array of strings in the original, so it is left as
 * a loosely typed {@link Object} and normalized in the service layer. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DriverForNotificationRequest {

    private String action;
    private Object mobile;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Object getMobile() {
        return mobile;
    }

    public void setMobile(Object mobile) {
        this.mobile = mobile;
    }
}
