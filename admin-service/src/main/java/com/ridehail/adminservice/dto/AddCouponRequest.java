package com.ridehail.adminservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Bean-Validation port of validations/coupon.validation.js#addCouponValidation. Bound from the
 * multipart form of POST /admin-apis/add-coupon (the {@code image} file part is handled separately
 * in the controller). {@code startDate}/{@code expiryDate} are read by the controller but have no
 * Joi rule in the original, so they are only structurally validated here.
 */
@Getter
@Setter
public class AddCouponRequest {

    @NotNull
    @Size(min = 3, max = 100)
    private String title;

    @NotNull
    @Size(min = 3, max = 500)
    private String description;

    @NotNull
    @Size(min = 3, max = 100)
    private String code;

    @NotNull
    @Pattern(regexp = "local|outstation")
    private String tripType;

    @NotNull
    @Pattern(regexp = "percentage|amount")
    private String discountType;

    @NotNull
    @DecimalMin("0")
    private BigDecimal discountValue;

    private String startDate;

    private String expiryDate;
}
