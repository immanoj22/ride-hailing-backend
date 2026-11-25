package com.ridehail.helperdeskservice.entity;

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
 * Equivalent of modals/sos.schema.js. The Mongo "location" GeoJSON Point (with a 2dsphere index)
 * is stored as plain lat/lng columns - no PostGIS extension is provisioned for this service, and
 * the original 2dsphere index was only ever queried by simple equality/sort filters (rideId,
 * userId, userType, createdAt) in helperdesk.controller.js/services.js, never a $near/$geoWithin
 * geo query, so real geo columns/indexes are not required to preserve behavior.
 */
@Entity
@Table(name = "sos_alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SosAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "legacy_mongo_id", length = 24, unique = true)
    private String legacyMongoId;

    /** ride-service owns rides - plain uuid, no FK. */
    @Column(name = "ride_id")
    private UUID rideId;

    /** user-service (rider) or driver-service owns this id depending on userType - no FK. */
    @Column(name = "user_id")
    private UUID userId;

    /** "rider" | "driver" | "" (sos.schema.js "userType", default ""). */
    @Builder.Default
    @Column(name = "user_type", nullable = false, length = 10)
    private String userType = "";

    @Column(name = "location_lat")
    private Double locationLat;

    @Column(name = "location_lng")
    private Double locationLng;

    @Builder.Default
    @Column(name = "triggered_at", nullable = false)
    private OffsetDateTime triggeredAt = OffsetDateTime.now();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
