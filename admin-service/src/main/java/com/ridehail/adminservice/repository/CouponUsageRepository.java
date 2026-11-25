package com.ridehail.adminservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ridehail.adminservice.entity.CouponUsage;

public interface CouponUsageRepository extends JpaRepository<CouponUsage, UUID> {

    /** get_user_coupons: {@code getCouponUsage({query:{userId}, select:{couponId:1}})}. */
    List<CouponUsage> findByUserId(UUID userId);

    /** consume_coupon: {@code getCouponUsage({query:{couponId, userId}})}. */
    Optional<CouponUsage> findByCouponIdAndUserId(UUID couponId, UUID userId);
}
