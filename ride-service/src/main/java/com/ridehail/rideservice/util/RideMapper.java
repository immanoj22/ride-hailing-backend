package com.ridehail.rideservice.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ridehail.rideservice.entity.Ride;
import com.ridehail.rideservice.entity.RideAdminPayment;
import com.ridehail.rideservice.entity.RideDriverPayment;
import com.ridehail.rideservice.entity.RideProcess;

/**
 * Rebuilds the legacy Mongoose "Ride" document JSON shape (field names verbatim from
 * modals/ride.schema.js) from a JPA {@link Ride} entity, so ported read endpoints return an
 * identical wire body to the Node service.
 */
public final class RideMapper {

    private RideMapper() {
    }

    public static Map<String, Object> toLegacyMap(Ride r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", r.getId());
        m.put("promo", r.getPromo());
        m.put("bookingNumber", r.getBookingNumber());
        m.put("pickup", latLng(r.getPickupLat(), r.getPickupLng()));
        m.put("drop", latLng(r.getDropLat(), r.getDropLng()));

        Map<String, Object> location = new LinkedHashMap<>();
        location.put("pickup", r.getLocationPickup());
        location.put("dropoff", r.getLocationDropoff());
        location.put("pickupDistrict", r.getLocationPickupDistrict());
        location.put("dropoffDistrict", r.getLocationDropoffDistrict());
        m.put("location", location);

        m.put("userId", r.getUserId());
        m.put("driverId", r.getDriverId());
        m.put("vehicleTypeId", r.getVehicleTypeId());
        m.put("vehicleVariantId", r.getVehicleVariantId());
        m.put("postalCodeId", r.getPostalCodeId());
        m.put("rideType", r.getRideType());
        m.put("paymentMethod", r.getPaymentMethod());
        m.put("status", r.getStatus());
        m.put("canceledBy", r.getCanceledBy());
        m.put("reason", r.getReason());
        m.put("time", r.getRideTimeSeconds());
        m.put("km", r.getKm());
        m.put("mcd", r.getMcd());
        m.put("priceId", r.getPriceId());
        m.put("totalPrice", r.getTotalPrice());
        m.put("paymentData", r.getPaymentData());
        m.put("transactionHistory", r.getTransactionHistory());
        m.put("cancelMinutes", r.getCancelMinutes());
        m.put("cancelAmount", r.getCancelAmount());
        m.put("cancelAmountPaid", r.isCancelAmountPaid());
        m.put("waitingAmount", r.getWaitingAmount());
        m.put("waitingAmountPaid", r.isWaitingAmountPaid());
        m.put("waitingMinute", r.getWaitingMinute());
        m.put("transitWaitingAmount", r.getTransitWaitingAmount());
        m.put("transitWaitingAmountPaid", r.isTransitWaitingAmountPaid());
        m.put("transitWaitingMinute", r.getTransitWaitingMinute());
        m.put("isToll", r.isToll());
        m.put("codPayment", r.getCodPayment());
        m.put("merchantTransactionId", r.getMerchantTransactionId());
        m.put("ignoredDrivers", r.getIgnoredDrivers());
        m.put("requestedDrivers", r.getRequestedDrivers());

        Map<String, Object> coordinates = new LinkedHashMap<>();
        coordinates.put("pickup", latLng(r.getCoordinatesPickupLat(), r.getCoordinatesPickupLng()));
        coordinates.put("dropoff", latLng(r.getCoordinatesDropoffLat(), r.getCoordinatesDropoffLng()));
        m.put("coordinates", coordinates);

        Map<String, Object> outstation = new LinkedHashMap<>();
        outstation.put("tripType", r.getOutstationTripType());
        outstation.put("adminCommission", r.getOutstationAdminCommission());
        outstation.put("extraKm", r.getOutstationExtraKm());
        outstation.put("extraTime", r.getOutstationExtraTime());
        outstation.put("kmEstimated", r.getOutstationKmEstimated());
        outstation.put("totalKm", r.getOutstationTotalKm());
        outstation.put("durationEstimated", r.getOutstationDurationEstimated());
        outstation.put("totalDuration", r.getOutstationTotalDuration());
        m.put("outstation", outstation);

        m.put("delete", r.isDeleted());
        m.put("cancelAmountOnRide", r.getCancelAmountOnRide());
        m.put("createdBy", r.getCreatedBy());
        m.put("bookingDate", r.getBookingDate());
        m.put("endDate", r.getEndDate());
        m.put("advancePayment", r.getAdvancePayment());
        m.put("paidBy", r.getPaidBy());
        m.put("otp", r.getOtp());
        m.put("isOtpVerify", r.isOtpVerify());
        m.put("multipleDrop", r.getMultipleDrop());
        m.put("notes", r.getNotes());
        m.put("extraCarrier", r.isExtraCarrier());
        m.put("upcomingTrip", r.isUpcomingTrip());
        m.put("process", processList(r.getProcess()));
        m.put("adminPayment", adminPaymentList(r.getAdminPayment()));
        m.put("driverPayment", driverPaymentList(r.getDriverPayment()));
        m.put("createdAt", r.getCreatedAt());
        m.put("updatedAt", r.getUpdatedAt());
        return m;
    }

    public static Map<String, Object> latLng(Double lat, Double lng) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lat", lat);
        m.put("lng", lng);
        return m;
    }

    private static List<Map<String, Object>> processList(List<RideProcess> src) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (src == null) {
            return out;
        }
        for (RideProcess p : src) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("_id", p.getId());
            m.put("status", p.getStatus());
            m.put("createdAt", p.getCreatedAt());
            m.put("updatedAt", p.getUpdatedAt());
            out.add(m);
        }
        return out;
    }

    private static List<Map<String, Object>> adminPaymentList(List<RideAdminPayment> src) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (src == null) {
            return out;
        }
        for (RideAdminPayment p : src) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("_id", p.getId());
            m.put("transaction", p.getTransaction());
            m.put("amount", p.getAmount());
            m.put("createdAt", p.getCreatedAt());
            m.put("updatedAt", p.getUpdatedAt());
            out.add(m);
        }
        return out;
    }

    private static List<Map<String, Object>> driverPaymentList(List<RideDriverPayment> src) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (src == null) {
            return out;
        }
        for (RideDriverPayment p : src) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("_id", p.getId());
            m.put("transaction", p.getTransaction());
            m.put("amount", p.getAmount());
            m.put("createdAt", p.getCreatedAt());
            m.put("updatedAt", p.getUpdatedAt());
            out.add(m);
        }
        return out;
    }
}
