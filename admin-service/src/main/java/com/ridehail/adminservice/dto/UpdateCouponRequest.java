package com.ridehail.adminservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Bean-Validation port of validations/coupon.validation.js#updateCouponValidation. Bound from the
 * multipart form of POST /admin-apis/update-coupon.
 */
@Getter
@Setter
public class UpdateCouponRequest {

    @NotBlank
    private String couponId;

    @Size(min = 3, max = 100)
    private String title;

    @Size(min = 3, max = 500)
    private String description;

    @Size(min = 3, max = 100)
    private String code;

    @Pattern(regexp = "local|outstation")
    private String tripType;

    @Pattern(regexp = "percentage|amount")
    private String discountType;

    @DecimalMin("0")
    private BigDecimal discountValue;

    private String startDate;

    private String expiryDate;

    /** legacy soft-delete flag, accepted as boolean or the string "true". */
    private String delete;
}
