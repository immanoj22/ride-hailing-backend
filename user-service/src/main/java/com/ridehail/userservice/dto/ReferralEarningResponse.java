package com.ridehail.userservice.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * One element of GET /user-apis/referral-earnings's {@code earnings} array, mirrors the
 * {@code .lean()} projection of modals/userReferralEarning.schema.js with
 * {@code referredUserId} populated (firstname/lastname/fullname/mobile/profile only).
 */
@Getter
@Setter
@AllArgsConstructor
public class ReferralEarningResponse {

    @JsonProperty("_id")
    private UUID id;

    private UUID referrerId;

    private ReferredUserSummary referredUserId;

    private BigDecimal amount;

    private String rewardType;

    private BigDecimal discountPercent;

    private Integer tripCount;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    @Getter
    @Setter
    @AllArgsConstructor
    public static class ReferredUserSummary {

        @JsonProperty("_id")
        private UUID id;

        private String firstname;

        private String lastname;

        private String fullname;

        private String mobile;

        private String profile;
    }
}
