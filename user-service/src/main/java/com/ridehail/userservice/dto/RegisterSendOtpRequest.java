package com.ridehail.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** PUT /register-send-otp. Mirrors uservalidation.js's registerOtpSchema. */
public class RegisterSendOtpRequest {

    @NotBlank
    @Size(min = 10, max = 10)
    private String mobile;

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }
}
