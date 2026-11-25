package com.ridehail.adminservice.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * Request body of POST /admin-apis/consume-coupon. No Joi schema existed; the controller guards
 * couponId/userId/rideId itself. {@code discountAmount} defaults to 0.
 */
@Getter
@Setter
public class ConsumeCouponRequest {

    private String couponId;

    private String userId;

    private String rideId;

    private BigDecimal discountAmount = BigDecimal.ZERO;
}
