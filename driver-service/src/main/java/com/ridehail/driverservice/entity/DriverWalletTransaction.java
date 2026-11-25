package com.ridehail.driverservice.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Equivalent of modals/driverWalletTransaction.schema.js (Mongoose "driverWalletTransaction" model). */
@Entity
@Table(name = "driver_wallet_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverWalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "legacy_mongo_id", length = 24, unique = true)
    private String legacyMongoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @Builder.Default
    @Column(name = "requested_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal requestedAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "requested_date", nullable = false)
    private OffsetDateTime requestedDate = OffsetDateTime.now();

    /** "pending" | "rejected" | "approved" | "paid". */
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private String status = "pending";

    /** "credit" | "debit" | "claim" | "refund" | "adjustment" | "referral_bonus". */
    @Builder.Default
    @Column(name = "transaction_type", nullable = false, length = 20)
    private String transactionType = "credit";

    /** Free-form metadata, e.g. { "referredDriverId": "..." } for referral_bonus rows. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta_data", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private JsonNode metaData = JsonNodeFactory.instance.objectNode();

    /** Admin (or in-app) actor id that reviewed the claim - cross-service, no FK. */
    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    /** Admin (or in-app) actor id that paid the claim - cross-service, no FK. */
    @Column(name = "paid_by")
    private UUID paidBy;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Builder.Default
    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "note", nullable = false, length = 1000)
    private String note = "";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
