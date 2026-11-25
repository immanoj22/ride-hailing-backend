package com.ridehail.adminservice.repository;

import java.util.Optional;
import java.util.UUID;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ridehail.adminservice.entity.Coupon;

/**
 * get_coupon_list builds a dynamic query (free-text search on title/code OR'd together, plus
 * optional tripType/discountType/createdAt filters and pagination) - {@link JpaSpecificationExecutor}
 * lets the service layer compose that without a derived-query explosion.
 */
public interface CouponRepository extends JpaRepository<Coupon, UUID>, JpaSpecificationExecutor<Coupon> {

    /** add_coupon: {@code getAllCoupons({query:{code, delete:false}}})} uniqueness check. */
    Optional<Coupon> findByCodeAndDeletedFalse(String code);

    /** update_coupon: same uniqueness check excluding the coupon being edited. */
    Optional<Coupon> findByCodeAndDeletedFalseAndIdNot(String code, UUID id);

    /** update_coupon/send_coupon/get_coupon: {@code getCoupon({query:{_id, delete:false}})}. */
    Optional<Coupon> findByIdAndDeletedFalse(UUID id);

    /**
     * get_user_coupons match query: not-deleted, matching tripType, currently active window,
     * (no eligibility restriction OR the user is on the list), optional exact code filter.
     * The "id NOT IN usedCouponIds" part is applied in the service layer.
     */
    @Query(value = """
            SELECT * FROM coupons c
            WHERE c.is_deleted = false
              AND c.trip_type = :tripType
              AND c.start_date <= :now
              AND c.expiry_date >= :now
              AND (cardinality(c.eligible_users) = 0 OR CAST(:userId AS uuid) = ANY(c.eligible_users))
              AND (CAST(:code AS varchar) IS NULL OR c.code = :code)
            ORDER BY c.created_at DESC
            """, nativeQuery = true)
    List<Coupon> findEligibleForUser(@Param("tripType") String tripType,
                                     @Param("now") OffsetDateTime now,
                                     @Param("userId") UUID userId,
                                     @Param("code") String code);
}
