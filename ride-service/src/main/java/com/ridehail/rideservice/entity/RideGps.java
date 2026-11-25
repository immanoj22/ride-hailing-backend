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

/**
 * Equivalent of modals/ridegps.schema.js (Mongoose model/collection name "ridegps") - individual
 * GPS trail points recorded during a ride.
 */
@Entity
@Table(name = "ride_gps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideGps {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "legacy_mongo_id", length = 24, unique = true)
    private String legacyMongoId;

    /** rides is owned by this same service - real FK. */
    @Column(name = "ride_id")
    private UUID rideId;

    /** driver-service owns this id - plain uuid, no FK. */
    @Column(name = "driver_id")
    private UUID driverId;

    /** user-service owns this id - plain uuid, no FK. */
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "speed")
    private Double speed;

    @Column(name = "heading")
    private Double heading;

    @Column(name = "accuracy")
    private Double accuracy;

    @Builder.Default
    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt = OffsetDateTime.now();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
