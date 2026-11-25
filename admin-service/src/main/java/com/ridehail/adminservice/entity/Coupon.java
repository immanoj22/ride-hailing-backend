package com.ridehail.adminservice.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA equivalent of Admin-service/modals/coupon.schema.js.
 */
@Entity
@Table(name = "coupons")
@Getter
@Setter
@NoArgsConstructor
public class Coupon extends BaseEntity {

    @Column(name = "title", nullable = false)
    private String title = "";

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description = "";

    @Column(name = "code", nullable = false)
    private String code = "";

    @Column(name = "image", nullable = false)
    private String image = "";

    /** Mongoose enum: ["local", "outstation"], default "local". */
    @Column(name = "trip_type", nullable = false)
    private String tripType = "local";

    /** Mongoose enum: ["amount", "percentage"], default "amount". */
    @Column(name = "discount_type", nullable = false)
    private String discountType = "amount";

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue = BigDecimal.ZERO;

    @Column(name = "start_date", nullable = false)
    private OffsetDateTime startDate;

    @Column(name = "expiry_date", nullable = false)
    private OffsetDateTime expiryDate;

    /**
     * Mongoose {@code eligibleUsers: [ObjectId]} - ids owned by user-service, so no FK is
     * possible/allowed; stored as a native Postgres uuid[] array.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "eligible_users", nullable = false, columnDefinition = "uuid[]")
    private UUID[] eligibleUsers = new UUID[0];

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;
}
