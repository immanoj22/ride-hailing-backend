package com.ridehail.rideservice.entity;

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

/** Equivalent of modals/vehiclevarient.schema.js. */
@Entity
@Table(name = "vehicle_variants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "legacy_mongo_id", length = 24, unique = true)
    private String legacyMongoId;

    /** vehicle_types is owned by this same service - real FK. */
    @Column(name = "vehicle_type_id")
    private UUID vehicleTypeId;

    @Builder.Default
    @Column(name = "type", nullable = false, length = 50)
    private String type = "";

    @Builder.Default
    @Column(name = "label", nullable = false)
    private String label = "";

    @Builder.Default
    @Column(name = "description", nullable = false)
    private String description = "";

    @Builder.Default
    @Column(name = "image", nullable = false)
    private String image = "";

    @Builder.Default
    @Column(name = "is_local", nullable = false)
    private boolean local = false;

    @Builder.Default
    @Column(name = "is_outstation", nullable = false)
    private boolean outstation = false;

    /** prices is owned by this same service - real FK (added via ALTER TABLE in V1__init.sql). */
    @Column(name = "local_price_id")
    private UUID localPriceId;

    @Column(name = "outstation_price_id")
    private UUID outstationPriceId;

    @Builder.Default
    @Column(name = "seats", nullable = false)
    private Integer seats = 2;

    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
