package com.ridehail.adminservice.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA equivalent of Admin-service/modals/couponUsage.schema.js.
 * couponId is a real FK (Coupon lives in this same service's database); userId/rideId are
 * plain uuid columns with no FK since they are owned by user-service/ride-service respectively.
 */
@Entity
@Table(name = "coupon_usages")
@Getter
@Setter
@NoArgsConstructor
public class CouponUsage extends BaseEntity {

    @Column(name = "coupon_id")
    private UUID couponId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "ride_id")
    private UUID rideId;

    @Column(name = "used_at", nullable = false)
    private OffsetDateTime usedAt;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;
}
