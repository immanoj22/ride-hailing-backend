package com.ridehail.userservice.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

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
 * Rider account, mirrors modals/user.schema.js ("Users" Mongoose model / "users" collection).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Nullable/unique backfill column for the original Mongo _id (data-migration only). */
    @Column(name = "legacy_mongo_id", length = 24, unique = true)
    private String legacyMongoId;

    @Builder.Default
    @Column(nullable = false)
    private String firstname = "";

    @Builder.Default
    @Column(nullable = false)
    private String lastname = "";

    @Builder.Default
    @Column(nullable = false)
    private String fullname = "";

    @Builder.Default
    @Column(nullable = false)
    private String fcm = "";

    @Builder.Default
    @Column(nullable = false)
    private Boolean socketstatus = false;

    @Builder.Default
    @Column(name = "socket_id", nullable = false)
    private String socketId = "";

    @Builder.Default
    @Column(nullable = false)
    private String state = "";

    @Builder.Default
    @Column(nullable = false)
    private String city = "";

    @Builder.Default
    @Column(name = "terms_and_service_accept", nullable = false)
    private Boolean termsAndServiceAccept = false;

    /** Free-text in the original schema (no Mongoose enum) - kept as plain varchar. */
    @Builder.Default
    @Column(nullable = false)
    private String gender = "";

    /** Mongoose: unique + sparse. */
    @Column
    private String email;

    @Builder.Default
    @Column(nullable = false)
    private String profile = "";

    /** Mongoose: unique (see migration note re: nullable to avoid the Google-signup empty-string collision). */
    @Column
    private String mobile;

    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private Boolean deleted = false;

    /** Legacy vestigial field (always "" in the original schema/controllers); kept for fidelity. */
    @Builder.Default
    @Column(nullable = false)
    private String userid = "";

    @Builder.Default
    @Column(nullable = false)
    private String otp = "";

    /** Epoch millis, matches the original Number otpexpire field. */
    @Builder.Default
    @Column(name = "otp_expire", nullable = false)
    private Long otpExpire = 0L;

    @Builder.Default
    @Column(name = "otp_verify", nullable = false)
    private Boolean otpVerify = false;

    /** currentLocation GeoJSON Point -> plain lat/lng (no geo queries in the original service). */
    @Column(name = "current_location_lat")
    private Double currentLocationLat;

    @Column(name = "current_location_lng")
    private Double currentLocationLng;

    /** tripid: Mongoose Mixed array, stored as a raw JSON array. */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trip_ids", nullable = false, columnDefinition = "jsonb")
    private String tripIds = "[]";

    /** emergencyContacts: Mongoose Mixed array, stored as a raw JSON array. */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "emergency_contacts", nullable = false, columnDefinition = "jsonb")
    private String emergencyContacts = "[]";

    @Builder.Default
    @Column(name = "profile_skip", nullable = false)
    private Boolean profileSkip = false;

    // preferences.*
    @Builder.Default
    @Column(name = "allow_sms_invoice", nullable = false)
    private Boolean allowSmsInvoice = true;

    @Builder.Default
    @Column(name = "allow_whatsapp_updates", nullable = false)
    private Boolean allowWhatsAppUpdates = true;

    @Builder.Default
    @Column(name = "allow_email_invoice", nullable = false)
    private Boolean allowEmailInvoice = true;

    @Builder.Default
    @Column(name = "allow_mobile_notification", nullable = false)
    private Boolean allowMobileNotification = true;

    // wallet.*
    @Builder.Default
    @Column(name = "wallet_current_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal walletCurrentAmount = BigDecimal.ZERO;

    @Column(name = "wallet_last_updated", nullable = false)
    private OffsetDateTime walletLastUpdated;

    /** Application-guaranteed-unique code (see service/user.services.js#getRefCodeWithCheck). */
    @Column(name = "referral_code")
    private String referralCode;

    /** Self-reference within this service's own DB - real FK, no join fetched by default. */
    @Column(name = "referred_by")
    private UUID referredBy;

    /** referrals: Mongoose Mixed array of referred user ids, kept as a denormalized cache. */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String referrals = "[]";

    // referralReward.*  ("" in Mongoose == not-yet-granted == NULL here)
    @Column(name = "referral_reward_type")
    private String referralRewardType;

    @Builder.Default
    @Column(name = "referral_reward_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal referralRewardAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "referral_reward_discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal referralRewardDiscountPercent = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "referral_reward_remaining_trips", nullable = false)
    private Integer referralRewardRemainingTrips = 0;

    /** referralReward.usedRideIds: array of ride-service ids, no FK (owned by ride-service). */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "referral_reward_used_ride_ids", nullable = false, columnDefinition = "jsonb")
    private String referralRewardUsedRideIds = "[]";

    @Column(name = "referral_reward_granted_at")
    private OffsetDateTime referralRewardGrantedAt;

    @Builder.Default
    @Column(name = "wallet_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal walletBalance = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.walletLastUpdated == null) {
            this.walletLastUpdated = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
