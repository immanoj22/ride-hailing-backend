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

/** Equivalent of modals/rating.schema.js (Mongoose collection/model name "starratings"). */
@Entity
@Table(name = "star_ratings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StarRating {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "legacy_mongo_id", length = 24, unique = true)
    private String legacyMongoId;

    /** Mongoose default:'' on a Number field could never actually persist a numeric value that way -
     *  modeled here as nullable with no default. */
    @Column(name = "star_rating", precision = 3, scale = 2)
    private BigDecimal starRating;

    @Builder.Default
    @Column(name = "reason", nullable = false)
    private String reason = "";

    /** user-service owns this id - plain uuid, no FK. */
    @Column(name = "user_id")
    private UUID userId;

    /** driver-service owns this id - plain uuid, no FK. */
    @Column(name = "driver_id")
    private UUID driverId;

    /** "user" | "driver" - who submitted the rating (rating.schema.js "from"). */
    @Builder.Default
    @Column(name = "from_type", nullable = false, length = 10)
    private String fromType = "";

    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    /** rides is owned by this same service - real FK. */
    @Column(name = "ride_id")
    private UUID rideId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
