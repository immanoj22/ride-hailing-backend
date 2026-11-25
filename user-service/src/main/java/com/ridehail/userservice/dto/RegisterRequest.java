package com.ridehail.userservice.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

/**
 * POST /register. Mirrors uservalidation.js's registerSchema: {@code token} required when
 * {@code method=="google"}, {@code mobile}+{@code otp} (6 chars) required when
 * {@code method=="phone"} - Jakarta Bean Validation has no direct "when" equivalent, so those
 * conditional rules are reproduced as class-level {@code @AssertTrue} checks.
 */
public class RegisterRequest {

    @NotBlank
    private String method;

    private String token;
    private String mobile;
    private String otp;
    private String referredBy;
    private String fcm;

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }

    public String getReferredBy() {
        return referredBy;
    }

    public void setReferredBy(String referredBy) {
        this.referredBy = referredBy;
    }

    public String getFcm() {
        return fcm;
    }

    public void setFcm(String fcm) {
        this.fcm = fcm;
    }

    @AssertTrue(message = "token is required when method is google")
    public boolean isTokenValidForMethod() {
        return !"google".equals(method) || (token != null && !token.isBlank());
    }

    @AssertTrue(message = "mobile is required when method is phone")
    public boolean isMobileValidForMethod() {
        return !"phone".equals(method) || (mobile != null && !mobile.isBlank());
    }

    @AssertTrue(message = "otp must be 6 characters when method is phone")
    public boolean isOtpValidForMethod() {
        return !"phone".equals(method) || (otp != null && otp.length() == 6);
    }
}
