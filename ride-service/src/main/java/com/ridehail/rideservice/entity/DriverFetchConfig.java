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
 * Equivalent of modals/driverFetch.schema.js. Used as a small singleton-style config document in
 * the original app (get_driver_fetch_config / update_driver_fetch_config).
 */
@Entity
@Table(name = "driver_fetch_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverFetchConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "legacy_mongo_id", length = 24, unique = true)
    private String legacyMongoId;

    @Builder.Default
    @Column(name = "within_km", nullable = false, precision = 12, scale = 2)
    private BigDecimal withinKm = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "driver_count", nullable = false)
    private Integer driverCount = 0;

    @Builder.Default
    @Column(name = "request_per_driver", nullable = false)
    private Integer requestPerDriver = 0;

    @Builder.Default
    @Column(name = "km_restriction", nullable = false, precision = 12, scale = 2)
    private BigDecimal kmRestriction = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
