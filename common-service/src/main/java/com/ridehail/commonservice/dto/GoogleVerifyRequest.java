package com.ridehail.commonservice.dto;

import jakarta.validation.constraints.NotBlank;

public class GoogleVerifyRequest {

    @NotBlank
    private String token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
