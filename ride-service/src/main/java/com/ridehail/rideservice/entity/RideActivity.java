package com.ridehail.rideservice.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

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

/** Equivalent of modals/rideactivity.schema.js (Mongoose model name "Activity"). */
@Entity
@Table(name = "ride_activities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "legacy_mongo_id", length = 24, unique = true)
    private String legacyMongoId;

    /** rides is owned by this same service - real FK. */
    @Column(name = "ride_id", nullable = false)
    private UUID rideId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Builder.Default
    @Column(name = "ip", nullable = false, length = 64)
    private String ip = "";

    @Builder.Default
    @Column(name = "device", nullable = false)
    private String device = "";

    /** Mongoose {@code meta: {type: Object, default: {}}} - genuinely free-form metadata. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta", nullable = false, columnDefinition = "jsonb")
    private JsonNode meta;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
