package com.ridehail.rideservice.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Equivalent of modals/ridePaymentSchema.js. Tracks driver/admin/referral fee payouts owed for a
 * ride. The unique+partialFilterExpression Mongoose index (unique on rideId+action+paymentUserId
 * only when action="driverreferralfees") is reproduced as a Postgres partial unique index in
 * V1__init.sql (uq_ride_payments_referral_fees).
 */
@Entity
@Table(name = "ride_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RidePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "legacy_mongo_id", length = 24, unique = true)
    private String legacyMongoId;

    /** rides is owned by this same service - real FK. */
    @Column(name = "ride_id", nullable = false)
    private UUID rideId;

    /** "driverfees" | "adminfees" | "driverreferralfees" | "userreferralfees". */
    @Column(name = "action", nullable = false, length = 30)
    private String action;

    /** "user" | "driver" | "admin". */
    @Column(name = "payment_to", nullable = false, length = 10)
    private String paymentTo;

    @Builder.Default
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "is_claimed", nullable = false)
    private boolean claimed = false;

    /** Owned by user-service, driver-service or admin-service depending on paymentTo - no FK. */
    @Column(name = "payment_user_id")
    private UUID paymentUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
