package com.ridehail.userservice.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** Response data of POST /user-apis/consume-referral-discount. */
@Getter
@Setter
@AllArgsConstructor
public class ConsumeReferralDiscountResponse {

    private boolean applied;

    private BigDecimal discountPercent;

    private Integer remainingTrips;
}
