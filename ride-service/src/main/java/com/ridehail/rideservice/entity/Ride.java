package com.ridehail.rideservice.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Equivalent of modals/ride.schema.js's top-level "Ride" document - the central entity of this
 * service. Embedded repeating arrays (process[], adminPayment[], driverPayment[]) are modeled as
 * child entities ({@link RideProcess}, {@link RideAdminPayment}, {@link RideDriverPayment}); the
 * embedded (_id:false) "outstation" subdocument is flattened into outstation_* columns here; the
 * genuinely free-form/variable-shape Mongoose {@code Object}/Mixed fields (promo, totalPrice,
 * paymentData, transactionHistory, multipleDrop) are kept as JSONB.
 *
 * Note: the original schema declares BOTH top-level pickup/drop lat-lng AND a duplicate
 * coordinates.pickup/coordinates.dropoff lat-lng - that duplication is preserved as-is (not "fixed")
 * per the migration ground rules.
 */
@Entity
@Table(name = "rides")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ride {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "legacy_mongo_id", length = 24, unique = true)
    private String legacyMongoId;

    /** Mongoose {@code promo: {type: Object, default: {}}} - free-form applied-promo snapshot. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "promo", nullable = false, columnDefinition = "jsonb")
    private JsonNode promo;

    @Column(name = "booking_number", unique = true, length = 64)
    private String bookingNumber;

    @Column(name = "pickup_lat")
    private Double pickupLat;

    @Column(name = "pickup_lng")
    private Double pickupLng;

    @Column(name = "drop_lat")
    private Double dropLat;

    @Column(name = "drop_lng")
    private Double dropLng;

    @Builder.Default
    @Column(name = "location_pickup", nullable = false)
    private String locationPickup = "";

    @Builder.Default
    @Column(name = "location_dropoff", nullable = false)
    private String locationDropoff = "";

    @Builder.Default
    @Column(name = "location_pickup_district", nullable = false)
    private String locationPickupDistrict = "";

    @Builder.Default
    @Column(name = "location_dropoff_district", nullable = false)
    private String locationDropoffDistrict = "";

    /** user-service owns this id - plain uuid, no FK. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** driver-service owns this id - plain uuid, no FK. */
    @Column(name = "driver_id")
    private UUID driverId;

    /** vehicle_types/vehicle_variants are owned by this same service - real FKs. */
    @Column(name = "vehicle_type_id")
    private UUID vehicleTypeId;

    @Column(name = "vehicle_variant_id")
    private UUID vehicleVariantId;

    @Column(name = "postal_code_id")
    private Integer postalCodeId;

    /** "local" | "outstation", default "local". */
    @Builder.Default
    @Column(name = "ride_type", nullable = false, length = 20)
    private String rideType = "local";

    /** "" | "cash" | "online" | "wallet", default "". */
    @Builder.Default
    @Column(name = "payment_method", nullable = false, length = 10)
    private String paymentMethod = "";

    /** create/accept/cancel/.../updated - see ride.schema.js status enum, default "create". */
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private String status = "create";

    /** "user" | "driver" | "system" | "" - not formally enum-declared in Mongoose. */
    @Builder.Default
    @Column(name = "canceled_by", nullable = false, length = 20)
    private String canceledBy = "";

    @Builder.Default
    @Column(name = "reason", nullable = false)
    private String reason = "";

    /** ride.schema.js "time" (seconds) - renamed to avoid the SQL reserved word TIME. */
    @Builder.Default
    @Column(name = "ride_time_seconds", nullable = false)
    private Integer rideTimeSeconds = 0;

    @Builder.Default
    @Column(name = "km", nullable = false, precision = 12, scale = 2)
    private BigDecimal km = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "mcd", nullable = false, precision = 12, scale = 2)
    private BigDecimal mcd = BigDecimal.ZERO;

    /** prices is owned by this same service - real FK. */
    @Column(name = "price_id")
    private UUID priceId;

    /** Mongoose {@code totalPrice: {type: Object, default: {}}} - the computed fare breakdown. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "total_price", nullable = false, columnDefinition = "jsonb")
    private JsonNode totalPrice;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payment_data", nullable = false, columnDefinition = "jsonb")
    private JsonNode paymentData;

    /** stores UPI payment history (ride.schema.js "transactionHistory"). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transaction_history", nullable = false, columnDefinition = "jsonb")
    private JsonNode transactionHistory;

    @Builder.Default
    @Column(name = "cancel_minutes", nullable = false)
    private Integer cancelMinutes = 0;

    @Builder.Default
    @Column(name = "cancel_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal cancelAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "cancel_amount_paid", nullable = false)
    private boolean cancelAmountPaid = false;

    @Builder.Default
    @Column(name = "waiting_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal waitingAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "waiting_amount_paid", nullable = false)
    private boolean waitingAmountPaid = false;

    @Builder.Default
    @Column(name = "waiting_minute", nullable = false)
    private Integer waitingMinute = 0;

    @Builder.Default
    @Column(name = "transit_waiting_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal transitWaitingAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "transit_waiting_amount_paid", nullable = false)
    private boolean transitWaitingAmountPaid = false;

    @Builder.Default
    @Column(name = "transit_waiting_minute", nullable = false)
    private Integer transitWaitingMinute = 0;

    @Builder.Default
    @Column(name = "is_toll", nullable = false)
    private boolean toll = false;

    @Builder.Default
    @Column(name = "cod_payment", nullable = false, precision = 12, scale = 2)
    private BigDecimal codPayment = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "merchant_transaction_id", nullable = false)
    private String merchantTransactionId = "";

    /** driver-service owns these ids - plain uuid[] array, no FK/join table. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Builder.Default
    @Column(name = "ignored_drivers", columnDefinition = "uuid[]", nullable = false)
    private UUID[] ignoredDrivers = new UUID[0];

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Builder.Default
    @Column(name = "requested_drivers", columnDefinition = "uuid[]", nullable = false)
    private UUID[] requestedDrivers = new UUID[0];

    /** Duplicate of pickup/drop above in the original Mongoose schema - preserved as-is. */
    @Column(name = "coordinates_pickup_lat")
    private Double coordinatesPickupLat;

    @Column(name = "coordinates_pickup_lng")
    private Double coordinatesPickupLng;

    @Column(name = "coordinates_dropoff_lat")
    private Double coordinatesDropoffLat;

    @Column(name = "coordinates_dropoff_lng")
    private Double coordinatesDropoffLng;

    /** "" | "oneway" | "round" | "rental", default "" (ride.schema.js OutstationInfoSchema.tripType). */
    @Builder.Default
    @Column(name = "outstation_trip_type", nullable = false, length = 10)
    private String outstationTripType = "";

    @Builder.Default
    @Column(name = "outstation_admin_commission", nullable = false, length = 20)
    private String outstationAdminCommission = "notPaid";

    @Builder.Default
    @Column(name = "outstation_extra_km", nullable = false, precision = 12, scale = 2)
    private BigDecimal outstationExtraKm = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "outstation_extra_time", nullable = false)
    private Integer outstationExtraTime = 0;

    @Builder.Default
    @Column(name = "outstation_km_estimated", nullable = false, precision = 12, scale = 2)
    private BigDecimal outstationKmEstimated = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "outstation_total_km", nullable = false, precision = 12, scale = 2)
    private BigDecimal outstationTotalKm = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "outstation_duration_estimated", nullable = false)
    private Integer outstationDurationEstimated = 0;

    @Builder.Default
    @Column(name = "outstation_total_duration", nullable = false)
    private Integer outstationTotalDuration = 0;

    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Builder.Default
    @Column(name = "cancel_amount_on_ride", nullable = false, precision = 12, scale = 2)
    private BigDecimal cancelAmountOnRide = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "created_by", nullable = false, length = 20)
    private String createdBy = "user";

    @Builder.Default
    @Column(name = "booking_date", nullable = false)
    private OffsetDateTime bookingDate = OffsetDateTime.now();

    @Column(name = "end_date")
    private OffsetDateTime endDate;

    @Builder.Default
    @Column(name = "advance_payment", nullable = false, length = 20)
    private String advancePayment = "";

    @Builder.Default
    @Column(name = "paid_by", nullable = false, length = 20)
    private String paidBy = "";

    @Builder.Default
    @Column(name = "otp", nullable = false, length = 10)
    private String otp = "";

    @Builder.Default
    @Column(name = "is_otp_verify", nullable = false)
    private boolean otpVerify = false;

    /** Mongoose {@code multipleDrop: {type: Array, default: []}} - free-form list of drop points. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "multiple_drop", nullable = false, columnDefinition = "jsonb")
    private JsonNode multipleDrop;

    @Builder.Default
    @Column(name = "notes", nullable = false)
    private String notes = "";

    @Builder.Default
    @Column(name = "extra_carrier", nullable = false)
    private boolean extraCarrier = false;

    @Builder.Default
    @Column(name = "upcoming_trip", nullable = false)
    private boolean upcomingTrip = false;

    @Builder.Default
    @OneToMany(mappedBy = "ride", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<RideProcess> process = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "ride", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<RideAdminPayment> adminPayment = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "ride", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<RideDriverPayment> driverPayment = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
