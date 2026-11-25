package com.ridehail.userservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

/**
 * Body of PATCH /user-apis/add-refferal, mirrors validations/uservalidation.js's addRefSchema
 * ({@code referralCode} required) plus the unvalidated {@code _id} field the original controller
 * reads directly off {@code req.body} (user.controller.js#add_referer).
 */
@Getter
@Setter
public class AddRefererRequest {

    @NotBlank(message = "referralCode is required")
    private String referralCode;

    @JsonProperty("_id")
    private String id;
}
