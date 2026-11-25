package com.ridehail.userservice.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** Response data of GET /user-apis/referral-earnings. */
@Getter
@Setter
@AllArgsConstructor
public class ReferralEarningsResponse {

    private BigDecimal totalEarnings;

    private List<ReferralEarningResponse> earnings;
}
