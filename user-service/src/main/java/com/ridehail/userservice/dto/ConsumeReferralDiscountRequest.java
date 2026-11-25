package com.ridehail.userservice.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Body of POST /user-apis/consume-referral-discount (no Joi schema in the original route either -
 * userId/rideId are validated in the service, matching
 * user.controller.js#consume_referral_discount).
 */
@Getter
@Setter
public class ConsumeReferralDiscountRequest {

    private String userId;

    private String rideId;
}
