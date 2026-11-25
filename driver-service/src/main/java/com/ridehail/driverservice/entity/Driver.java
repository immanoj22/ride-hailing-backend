package com.ridehail.driverservice.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

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
 * Equivalent of modals/driver.schema.js (Mongoose "driver" model).
 *
 * <p>Notes on fields intentionally NOT copied 1:1 from the Mongoose schema:
 * <ul>
 *   <li>{@code documents} (embedded array, each with its own sub-_id) -> {@link DriverDocument}
 *       child table.</li>
 *   <li>{@code emergencyContacts} (embedded array, each with its own sub-_id) ->
 *       {@link DriverEmergencyContact} child table.</li>
 *   <li>{@code bankDetails} (single embedded object, no own _id) -> flattened onto this table as
 *       {@code bank*} columns.</li>
 *   <li>{@code trips} (bounded last-5 ride ids, ride-service owned) -> {@link #recentTripIds},
 *       a plain {@code uuid[]} column (no FK - different service's data).</li>
 *   <li>{@code referrals} (array, inverse of {@code referredBy}) -> intentionally dropped; derive
 *       via {@code DriverRepository.findByReferredBy(driverId)} instead of duplicating state.</li>
 *   <li>{@code wallet} (ref to driverWallet) -> intentionally dropped; {@link DriverWallet} already
 *       carries the owning {@code driverId}, so look it up via
 *       {@code DriverWalletRepository.findByDriverId(driverId)}.</li>
 * </ul>
 */
@Entity
@Table(name = "drivers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "legacy_mongo_id", length = 24, unique = true)
    private String legacyMongoId;

    @Column(name = "mobile", length = 20)
    private String mobile;

    @Column(name = "email")
    private String email;

    @Builder.Default
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName = "";

    @Builder.Default
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName = "";

    @Builder.Default
    @Column(name = "gender", nullable = false, length = 20)
    private String gender = "";

    @Builder.Default
    @Column(name = "state", nullable = false, length = 100)
    private String state = "";

    @Builder.Default
    @Column(name = "city", nullable = false, length = 100)
    private String city = "";

    @Builder.Default
    @Column(name = "profile_completed", nullable = false)
    private boolean profileCompleted = false;

    @Builder.Default
    @Column(name = "fcm_token", nullable = false, length = 1000)
    private String fcmToken = "";

    @Builder.Default
    @Column(name = "socket_id", nullable = false)
    private String socketId = "";

    @Builder.Default
    @Column(name = "socket_online", nullable = false)
    private boolean socketOnline = false;

    /** "pending" | "verified" | "rejected" (driver.schema.js "status"). */
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private String status = "pending";

    @Builder.Default
    @Column(name = "kyc_completed", nullable = false)
    private boolean kycCompleted = false;

    /** "pending" | "approved" | "rejected" | "suspended". */
    @Builder.Default
    @Column(name = "kyc_status", nullable = false, length = 20)
    private String kycStatus = "pending";

    @Builder.Default
    @Column(name = "profile_image", nullable = false, length = 500)
    private String profileImage = "";

    @Builder.Default
    @Column(name = "unverified_driver", nullable = false)
    private boolean unverifiedDriver = true;

    @Builder.Default
    @Column(name = "vehicle_number", nullable = false, length = 50)
    private String vehicleNumber = "";

    @Builder.Default
    @Column(name = "chassis_number", nullable = false, length = 100)
    private String chassisNumber = "";

    @Builder.Default
    @Column(name = "engine_number", nullable = false, length = 100)
    private String engineNumber = "";

    @Builder.Default
    @Column(name = "fuel_type", nullable = false, length = 50)
    private String fuelType = "";

    /** vehicle-service owns vehicle types - plain uuid, no FK. */
    @Column(name = "vehicle_type")
    private UUID vehicleType;

    /** vehicle-service owns vehicle variants - plain uuid, no FK. */
    @Column(name = "vehicle_variant")
    private UUID vehicleVariant;

    /** offline | online | requestride | rideaccept | pickup | startride | endtrip | searchrides. */
    @Builder.Default
    @Column(name = "driver_status", nullable = false, length = 20)
    private String driverStatus = "offline";

    @Column(name = "last_online_at")
    private OffsetDateTime lastOnlineAt;

    @Column(name = "current_latitude")
    private Double currentLatitude;

    @Column(name = "current_longitude")
    private Double currentLongitude;

    /** driver.schema.js "preferredLocations" - untyped array, kept as jsonb (see migration note). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_locations", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private JsonNode preferredLocations = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();

    @Builder.Default
    @Column(name = "on_ride", nullable = false)
    private boolean onRide = false;

    /** Mongoose field name "delete" (reserved word in SQL) - column renamed to is_deleted. */
    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Builder.Default
    @Column(name = "delete_reason", nullable = false, length = 500)
    private String deleteReason = "";

    /** ride-service owns trips - last few trip ids only, no FK. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "recent_trip_ids", columnDefinition = "uuid[]", nullable = false)
    @Builder.Default
    private UUID[] recentTripIds = new UUID[0];

    @Column(name = "last_trip_at")
    private OffsetDateTime lastTripAt;

    @Builder.Default
    @Column(name = "bank_account_number", nullable = false, length = 50)
    private String bankAccountNumber = "";

    @Builder.Default
    @Column(name = "bank_ifsc", nullable = false, length = 20)
    private String bankIfsc = "";

    @Builder.Default
    @Column(name = "bank_holder_name", nullable = false, length = 150)
    private String bankHolderName = "";

    @Builder.Default
    @Column(name = "bank_name", nullable = false, length = 150)
    private String bankName = "";

    @Builder.Default
    @Column(name = "bank_branch", nullable = false, length = 150)
    private String bankBranch = "";

    @Builder.Default
    @Column(name = "bank_upi_id", nullable = false, length = 150)
    private String bankUpiId = "";

    @Builder.Default
    @Column(name = "bank_upi_qr_image", nullable = false, length = 500)
    private String bankUpiQrImage = "";

    @Builder.Default
    @Column(name = "otp", nullable = false, length = 10)
    private String otp = "";

    @Column(name = "otp_expires_at")
    private OffsetDateTime otpExpiresAt;

    @Column(name = "referral_code", length = 20)
    private String referralCode;

    /** Self-referencing FK - internal relationship (driver.schema.js "referredBy" ref: "driver"). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referred_by")
    private Driver referredBy;

    @Builder.Default
    @Column(name = "is_blocked", nullable = false)
    private boolean blocked = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
