package com.ridehail.rideservice.service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.ridehail.common.constants.AuthUserType;
import com.ridehail.common.exception.ApiException;
import com.ridehail.common.security.AuthContext;
import com.ridehail.rideservice.entity.Price;
import com.ridehail.rideservice.entity.VehicleType;
import com.ridehail.rideservice.entity.VehicleVariant;

/** Shared helpers for the vehicle-catalog bucket services (role guard + legacy-shaped JSON maps). */
final class VehicleCatalogSupport {

    private VehicleCatalogSupport() {
    }

    /** middlewares/ride.auth.middleware.js authUserMiddleware(admin): allow admin or in-app only. */
    static void requireAdmin() {
        AuthContext ctx = AuthContext.current();
        AuthUserType type = ctx == null ? null : ctx.authUserType();
        if (type != AuthUserType.ADMIN && type != AuthUserType.IN_APP) {
            throw new ApiException(403, "This is only admin API.");
        }
    }

    static UUID toUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static Map<String, Object> vehicleTypeMap(VehicleType v) {
        if (v == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", v.getId());
        m.put("type", v.getType());
        m.put("label", v.getLabel());
        m.put("description", v.getDescription());
        m.put("image", v.getImage());
        m.put("delete", v.isDeleted());
        m.put("isHided", v.isHided());
        m.put("createdAt", v.getCreatedAt());
        m.put("updatedAt", v.getUpdatedAt());
        return m;
    }

    static Map<String, Object> vehicleVariantMap(VehicleVariant v, Object vehicleTypeId,
                                                 Object localPrice, Object outstationPrice) {
        if (v == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", v.getId());
        m.put("vehicleTypeId", vehicleTypeId != null ? vehicleTypeId : v.getVehicleTypeId());
        m.put("type", v.getType());
        m.put("label", v.getLabel());
        m.put("description", v.getDescription());
        m.put("image", v.getImage());
        m.put("isLocal", v.isLocal());
        m.put("isOutstation", v.isOutstation());
        m.put("localPrice", localPrice != null ? localPrice : v.getLocalPriceId());
        m.put("outstationPrice", outstationPrice != null ? outstationPrice : v.getOutstationPriceId());
        m.put("seats", v.getSeats());
        m.put("delete", v.isDeleted());
        m.put("createdAt", v.getCreatedAt());
        m.put("updatedAt", v.getUpdatedAt());
        return m;
    }

    static Map<String, Object> priceMap(Price p) {
        if (p == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", p.getId());
        m.put("vehicleTypeId", p.getVehicleTypeId());
        m.put("vehicleVariantId", p.getVehicleVariantId());
        m.put("brand", p.getBrand());
        m.put("pricePerKm", p.getPricePerKm());
        m.put("pricePerMin", p.getPricePerMin());
        m.put("priceSurge", p.getPriceSurge());
        m.put("basePrice", p.getBasePrice());
        m.put("pricePerDay", p.getPricePerDay());
        m.put("pricePerExtraKm", p.getPricePerExtraKm());
        m.put("pricePerExtraMin", p.getPricePerExtraMin());
        m.put("insurance", p.getInsurance());
        m.put("tollTax", p.getTollTax());
        m.put("mcdTax", p.getMcdTax());
        m.put("gstPercent", p.getGstPercent());
        m.put("commissionPercent", p.getCommissionPercent());
        m.put("peakTimeCharge", p.getPeakTimeCharge());
        m.put("userCancellationTimeLimitMinutes", p.getUserCancellationTimeLimitMinutes());
        m.put("userCancellationCharge", p.getUserCancellationCharge());
        m.put("waitingTimeLimitMinutes", p.getWaitingTimeLimitMinutes());
        m.put("waitingChargePerMin", p.getWaitingChargePerMin());
        m.put("transitWaitingFeePerMin", p.getTransitWaitingFeePerMin());
        m.put("others1", p.getOthers1());
        m.put("others2", p.getOthers2());
        m.put("others3", p.getOthers3());
        m.put("paymentModeSetting", p.getPaymentModeSetting());
        m.put("adminCommissionPrice", p.getAdminCommissionPrice());
        m.put("driverCommissionPrice", p.getDriverCommissionPrice());
        m.put("delete", p.isDeleted());
        m.put("outstation", outstationMap(p));
        m.put("createdAt", p.getCreatedAt());
        m.put("updatedAt", p.getUpdatedAt());
        return m;
    }

    static Map<String, Object> outstationMap(Price p) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("extraKmFare", p.getOutstationExtraKmFare());
        out.put("extraMinuteFare", p.getOutstationExtraMinuteFare());
        out.put("waitingCharge", p.getOutstationWaitingCharge());
        out.put("cancelCharge", p.getOutstationCancelCharge());
        Map<String, Object> oneWayRound = new LinkedHashMap<>();
        oneWayRound.put("driverAllowance", p.getOutstationOneWayRoundDriverAllowance());
        oneWayRound.put("packages", p.getOnewayPackages().stream().map(pkg -> {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("_id", pkg.getId());
            pm.put("fromKm", pkg.getFromKm());
            pm.put("toKm", pkg.getToKm());
            pm.put("baseFare", pkg.getBaseFare());
            return pm;
        }).toList());
        out.put("oneWayRound", oneWayRound);
        Map<String, Object> rental = new LinkedHashMap<>();
        rental.put("packages", p.getRentalPackages().stream().map(pkg -> {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("_id", pkg.getId());
            pm.put("durationType", pkg.getDurationType());
            pm.put("durationValue", pkg.getDurationValue());
            pm.put("kmLimit", pkg.getKmLimit());
            pm.put("baseFare", pkg.getBaseFare());
            return pm;
        }).toList());
        out.put("rental", rental);
        return out;
    }

    static OffsetDateTime now() {
        return OffsetDateTime.now();
    }
}
