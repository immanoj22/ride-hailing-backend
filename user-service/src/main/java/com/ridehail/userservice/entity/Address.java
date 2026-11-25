package com.ridehail.userservice.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Rider saved address, mirrors modals/address.schema.js ("address" model / "addresses" collection).
 */
@Entity
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "legacy_mongo_id", length = 24, unique = true)
    private String legacyMongoId;

    /** Same-service reference (users table) - real FK, plain id (no managed association). */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Builder.Default
    @Column(nullable = false)
    private String label = "";

    @Builder.Default
    @Column(nullable = false)
    private String street1 = "";

    @Builder.Default
    @Column(nullable = false)
    private String street2 = "";

    @Builder.Default
    @Column(nullable = false)
    private String district = "";

    @Builder.Default
    @Column(nullable = false)
    private String landmark = "";

    /** "home" | "work" | "other" - enforced by a CHECK constraint in the migration. */
    @Builder.Default
    @Column(nullable = false)
    private String type = "home";

    /** Mongoose stored this as a String("0"/"1"); modelled as a real boolean here. */
    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private Boolean deleted = false;

    /** coordinate GeoJSON Point -> plain lat/lng. */
    @Column(name = "coordinate_lat")
    private Double coordinateLat;

    @Column(name = "coordinate_lng")
    private Double coordinateLng;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
