package com.ridehail.adminservice.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA equivalent of Admin-service/modals/settings.schema.js. In practice this is a singleton
 * row (the original service always does {@code settings.findOne({})} / an upsert with an empty
 * filter), but no application-level uniqueness constraint existed on the Mongoose model either.
 */
@Entity
@Table(name = "settings")
@Getter
@Setter
@NoArgsConstructor
public class Settings extends BaseEntity {

    @Column(name = "ios_version", nullable = false)
    private String iosVersion = "";

    @Column(name = "android_version", nullable = false)
    private String androidVersion = "";

    @Column(name = "ios_link", nullable = false)
    private String iosLink = "";

    @Column(name = "android_link", nullable = false)
    private String androidLink = "";

    @Column(name = "driver_referral_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal driverReferralPercent = BigDecimal.ONE;

    @Column(name = "user_referral_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal userReferralPercent = BigDecimal.ONE;

    @Column(name = "referral_enabled", nullable = false)
    private boolean referralEnabled = true;

    /** Mongoose enum: ["wallet_credit", "trip_discount"], default "wallet_credit". */
    @Column(name = "customer_referral_reward_type", nullable = false)
    private String customerReferralRewardType = "wallet_credit";

    @Column(name = "customer_referral_wallet_credit", nullable = false, precision = 12, scale = 2)
    private BigDecimal customerReferralWalletCredit = BigDecimal.ZERO;

    @Column(name = "customer_referral_discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal customerReferralDiscountPercent = BigDecimal.ZERO;

    @Column(name = "customer_referral_discount_trip_count", nullable = false)
    private Integer customerReferralDiscountTripCount = 1;

    @Column(name = "driver_referral_revenue_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal driverReferralRevenuePercent = BigDecimal.ZERO;

    @Column(name = "admin_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal adminPercent = BigDecimal.ONE;

    @Column(name = "user_max_count", nullable = false)
    private Integer userMaxCount = 1;

    @Column(name = "local_ride_km_limit", nullable = false, precision = 8, scale = 2)
    private BigDecimal localRideKmLimit = BigDecimal.ONE;
}
