package com.ridehail.adminservice.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Request body of POST /admin-apis/get-user-coupons. No Joi schema existed in the original;
 * the controller guards {@code userId}/{@code tripType} itself. {@code couponId} here is used by
 * the original as a coupon <em>code</em> filter (see admin.controller.js#get_user_coupons).
 */
@Getter
@Setter
public class GetUserCouponsRequest {

    private String userId;

    private String tripType;

    private String couponId;
}
