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
 * Equivalent of modals/price.schema.js's top-level "Price" document. The embedded
 * outstation.oneWayRound.packages[] / outstation.rental.packages[] arrays are modeled as the child
 * entities {@link PriceOnewayPackage} / {@link PriceRentalPackage} (real repeating rows, not a
 * JSONB blob), per the DATABASE RULES. The rest of the embedded (_id:false) "outstation" subdocument
 * is flattened into outstation_* columns directly on this entity.
 */
@Entity
@Table(name = "prices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Price {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "legacy_mongo_id", length = 24, unique = true)
    private String legacyMongoId;

    /** vehicle_types/vehicle_variants are owned by this same service - real FKs. */
    @Column(name = "vehicle_type_id")
    private UUID vehicleTypeId;

    @Column(name = "vehicle_variant_id")
    private UUID vehicleVariantId;

    @Builder.Default
    @Column(name = "brand", nullable = false)
    private String brand = "";

    @Builder.Default
    @Column(name = "price_per_km", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerKm = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "price_per_min", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerMin = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "price_surge", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceSurge = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "price_per_day", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerDay = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "price_per_extra_km", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerExtraKm = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "price_per_extra_min", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerExtraMin = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "insurance", nullable = false, precision = 12, scale = 2)
    private BigDecimal insurance = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "toll_tax", nullable = false, precision = 12, scale = 2)
    private BigDecimal tollTax = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "mcd_tax", nullable = false, precision = 12, scale = 2)
    private BigDecimal mcdTax = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "gst_percent", nullable = false, precision = 12, scale = 2)
    private BigDecimal gstPercent = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "commission_percent", nullable = false, precision = 12, scale = 2)
    private BigDecimal commissionPercent = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "peak_time_charge", nullable = false, precision = 12, scale = 2)
    private BigDecimal peakTimeCharge = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "user_cancellation_time_limit_minutes", nullable = false)
    private Integer userCancellationTimeLimitMinutes = 0;

    @Builder.Default
    @Column(name = "user_cancellation_charge", nullable = false, precision = 12, scale = 2)
    private BigDecimal userCancellationCharge = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "waiting_time_limit_minutes", nullable = false)
    private Integer waitingTimeLimitMinutes = 0;

    @Builder.Default
    @Column(name = "waiting_charge_per_min", nullable = false, precision = 12, scale = 2)
    private BigDecimal waitingChargePerMin = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "transit_waiting_fee_per_min", nullable = false, precision = 12, scale = 2)
    private BigDecimal transitWaitingFeePerMin = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "others1", nullable = false, precision = 12, scale = 2)
    private BigDecimal others1 = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "others2", nullable = false, precision = 12, scale = 2)
    private BigDecimal others2 = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "others3", nullable = false, precision = 12, scale = 2)
    private BigDecimal others3 = BigDecimal.ZERO;

    /** paymentModeSetting enum(["cash","upi","customer_wish"]) array. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Builder.Default
    @Column(name = "payment_mode_setting", columnDefinition = "text[]", nullable = false)
    private String[] paymentModeSetting = new String[0];

    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Builder.Default
    @Column(name = "admin_commission_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal adminCommissionPrice = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "driver_commission_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal driverCommissionPrice = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "outstation_extra_km_fare", nullable = false, precision = 12, scale = 2)
    private BigDecimal outstationExtraKmFare = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "outstation_extra_minute_fare", nullable = false, precision = 12, scale = 2)
    private BigDecimal outstationExtraMinuteFare = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "outstation_waiting_charge", nullable = false, precision = 12, scale = 2)
    private BigDecimal outstationWaitingCharge = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "outstation_cancel_charge", nullable = false, precision = 12, scale = 2)
    private BigDecimal outstationCancelCharge = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "outstation_one_way_round_driver_allowance", nullable = false, precision = 12, scale = 2)
    private BigDecimal outstationOneWayRoundDriverAllowance = BigDecimal.ZERO;

    @Builder.Default
    @OneToMany(mappedBy = "price", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("fromKm ASC")
    private List<PriceOnewayPackage> onewayPackages = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "price", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<PriceRentalPackage> rentalPackages = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
