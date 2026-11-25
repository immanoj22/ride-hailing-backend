package com.ridehail.adminservice.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * Bean-Validation port of validations/admin.validation.js#updateSettingsSchema. Every field is
 * optional (Mongoose {@code $set} of only the supplied keys). Field/JSON names are kept in the
 * original snake_case so the request shape is byte-identical. {@code admin_percent},
 * {@code user_max_count} and {@code local_ride_km_limit} are not in the Joi schema but are real
 * settings columns, accepted leniently.
 */
@Getter
@Setter
public class UpdateSettingsRequest {

    @Pattern(regexp = "\\d+(\\.\\d+){0,2}", message = "ios_version must be in x.x or x.x.x format")
    @JsonProperty("ios_version")
    private String iosVersion;

    @Pattern(regexp = "\\d+(\\.\\d+){0,2}", message = "android_version must be in x.x or x.x.x format")
    @JsonProperty("android_version")
    private String androidVersion;

    @JsonProperty("ios_link")
    private String iosLink;

    @JsonProperty("android_link")
    private String androidLink;

    @DecimalMin("0") @DecimalMax("100")
    @JsonProperty("driver_referral_percent")
    private BigDecimal driverReferralPercent;

    @DecimalMin("0") @DecimalMax("100")
    @JsonProperty("user_referral_percent")
    private BigDecimal userReferralPercent;

    @JsonProperty("referral_enabled")
    private Boolean referralEnabled;

    @Pattern(regexp = "wallet_credit|trip_discount")
    @JsonProperty("customer_referral_reward_type")
    private String customerReferralRewardType;

    @DecimalMin("0")
    @JsonProperty("customer_referral_wallet_credit")
    private BigDecimal customerReferralWalletCredit;

    @DecimalMin("0") @DecimalMax("100")
    @JsonProperty("customer_referral_discount_percent")
    private BigDecimal customerReferralDiscountPercent;

    @Min(1)
    @JsonProperty("customer_referral_discount_trip_count")
    private Integer customerReferralDiscountTripCount;

    @DecimalMin("0") @DecimalMax("100")
    @JsonProperty("driver_referral_revenue_percent")
    private BigDecimal driverReferralRevenuePercent;

    @JsonProperty("admin_percent")
    private BigDecimal adminPercent;

    @JsonProperty("user_max_count")
    private Integer userMaxCount;

    @JsonProperty("local_ride_km_limit")
    private BigDecimal localRideKmLimit;
}
