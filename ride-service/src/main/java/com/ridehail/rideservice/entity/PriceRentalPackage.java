package com.ridehail.rideservice.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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

/**
 * Child row of a {@link Price}'s embedded "outstation.rental.packages[]" array
 * (price.schema.js RentalPackageItemSchema: durationType/durationValue/kmLimit/baseFare).
 */
@Entity
@Table(name = "price_rental_packages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceRentalPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "legacy_mongo_id", length = 24, unique = true)
    private String legacyMongoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_id", nullable = false)
    private Price price;

    /** "hour" | "day" | "week", default "hour". */
    @Builder.Default
    @Column(name = "duration_type", nullable = false, length = 10)
    private String durationType = "hour";

    @Builder.Default
    @Column(name = "duration_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal durationValue = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "km_limit", nullable = false, precision = 12, scale = 2)
    private BigDecimal kmLimit = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "base_fare", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseFare = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
