package com.ridehail.adminservice.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * Bean-Validation port of validations/coupon.validation.js#sendCouponValidation. The
 * "phoneNumbers required when sendTo == INDIVIDUAL" conditional rule is enforced in the service
 * layer (matching the original controller's own guard).
 */
@Getter
@Setter
public class SendCouponRequest {

    @NotBlank
    private String couponId;

    @NotBlank
    @Pattern(regexp = "ALL|INDIVIDUAL")
    private String sendTo;

    private List<String> phoneNumbers;
}
