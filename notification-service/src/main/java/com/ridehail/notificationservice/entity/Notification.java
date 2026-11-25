package com.ridehail.notificationservice.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mirrors modal/notification.schema.js (Mongoose "Notification" model).
 *
 * <p>Note: {@code mobile} is a legacy misnomer in the original schema - the application actually
 * stores a JSON-stringified array of FCM tokens there (see the pushNotification controller
 * function), never an actual phone number. Preserved as-is (TEXT) rather than renamed.
 *
 * <p>{@code userIds} / {@code driverIds} reference records owned by user-service / driver-service
 * respectively, so they are plain {@code uuid[]} columns with no JPA relationship / foreign key.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "legacy_mongo_id", length = 24, unique = true)
    private String legacyMongoId;

    @Column(name = "image", nullable = false)
    @Builder.Default
    private String image = "";

    @Column(name = "title", nullable = false)
    @Builder.Default
    private String title = "";

    @Column(name = "message", nullable = false)
    @Builder.Default
    private String message = "";

    @Column(name = "body", nullable = false)
    @Builder.Default
    private String body = "";

    /** Original Mongoose field name is "Whom" (capitalized) - DTOs must @JsonProperty("Whom"). */
    @Column(name = "whom", nullable = false, length = 20)
    @Builder.Default
    private String whom = "";

    @Column(name = "mobile", nullable = false)
    @Builder.Default
    private String mobile = "";

    @Column(name = "coupons", nullable = false)
    @Builder.Default
    private String coupons = "";

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "user_ids", columnDefinition = "uuid[]", nullable = false)
    @Builder.Default
    private UUID[] userIds = new UUID[0];

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "driver_ids", columnDefinition = "uuid[]", nullable = false)
    @Builder.Default
    private UUID[] driverIds = new UUID[0];

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
