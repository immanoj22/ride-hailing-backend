package com.ridehail.rideservice.util;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.ridehail.rideservice.entity.Price;
import com.ridehail.rideservice.entity.PriceOnewayPackage;
import com.ridehail.rideservice.entity.PriceRentalPackage;

/**
 * Direct port of {@code calculateOutstationFare} in service/ride.service.js (see docs/PRICING_FLOW.md
 * section 3.1). Kept in the util package because the vehicle-catalog bucket
 * (getAllVehiclesWithKm / getOutstaionPriceForVehicle) needs it for the price preview; the ride
 * lifecycle bucket uses the same helper for create/update outstation ride flows.
 */
public final class OutstationFareCalculator {

    private OutstationFareCalculator() {
    }

    public static Map<String, Object> calculate(Price priceDoc,
                                                List<PriceOnewayPackage> onewayPackages,
                                                List<PriceRentalPackage> rentalPackages,
                                                String rentalPackageId,
                                                String tripType,
                                                double kmEstimated,
                                                JsonNode promo,
                                                double days) {

        Map<String, Object> breakdown = new LinkedHashMap<>();
        double driverAllowance = 0d;
        double tollTax = num(priceDoc.getTollTax());
        double gstPercent = num(priceDoc.getGstPercent());
        double commissionPercent = num(priceDoc.getCommissionPercent());

        breakdown.put("baseFare", 0d);
        breakdown.put("driverAllowance", driverAllowance);
        breakdown.put("tollTax", tollTax);
        breakdown.put("gstPercent", gstPercent);
        breakdown.put("commissionPercent", commissionPercent);
        breakdown.put("total", 0d);
        breakdown.put("gstAmount", 0d);
        breakdown.put("grandTotal", 0d);

        double baseFare;
        double kilometerPrice = 0d;

        if ("rental".equals(tripType)) {
            PriceRentalPackage pkg = null;
            if (rentalPackages != null) {
                for (PriceRentalPackage p : rentalPackages) {
                    if (p.getId() != null && p.getId().toString().equals(String.valueOf(rentalPackageId))) {
                        pkg = p;
                        break;
                    }
                }
            }
            if (pkg == null) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("error", "No rental package available, try again");
                return err;
            }
            baseFare = num(pkg.getBaseFare());
            breakdown.put("baseFare", baseFare);
            breakdown.put("km", num(pkg.getKmLimit()));
            breakdown.put("time", num(pkg.getDurationValue()));
            breakdown.put("timeType", pkg.getDurationType() == null ? "hour" : pkg.getDurationType());
        } else {
            PriceOnewayPackage matchPkg = null;
            PriceOnewayPackage bigValue = null;
            if (onewayPackages != null) {
                for (PriceOnewayPackage p : onewayPackages) {
                    double fromKm = num(p.getFromKm());
                    double toKm = num(p.getToKm());
                    if (bigValue == null || toKm > num(bigValue.getToKm())) {
                        bigValue = p;
                    }
                    if (kmEstimated >= fromKm && kmEstimated <= toKm) {
                        matchPkg = p;
                    }
                }
            }
            PriceOnewayPackage pkg = matchPkg != null ? matchPkg : bigValue;
            baseFare = pkg != null ? num(pkg.getBaseFare()) : num(priceDoc.getBasePrice());
            breakdown.put("baseFare", baseFare);

            kilometerPrice = days > 2
                    ? num(priceDoc.getPricePerDay()) * days
                    : num(priceDoc.getPricePerKm()) * kmEstimated;
            breakdown.put("kilometerPrice", kilometerPrice);

            if ("round".equals(tripType)) {
                driverAllowance = num(priceDoc.getOutstationOneWayRoundDriverAllowance());
                breakdown.put("driverAllowance", driverAllowance);
            }
        }

        breakdown.put("pricePerKm", num(priceDoc.getPricePerKm()));
        breakdown.put("pricePerMin", num(priceDoc.getPricePerMin()));
        breakdown.put("basePrice", num(priceDoc.getBasePrice()));
        breakdown.put("pricePerDay", num(priceDoc.getPricePerDay()));
        breakdown.put("insurance", num(priceDoc.getInsurance()));
        breakdown.put("peakTimeCharge", num(priceDoc.getPeakTimeCharge()));
        breakdown.put("waitingChargePerMin", num(priceDoc.getWaitingChargePerMin()));
        breakdown.put("transitWaitingFeePerMin", num(priceDoc.getTransitWaitingFeePerMin()));
        breakdown.put("waitingTimeLimitMinutes", priceDoc.getWaitingTimeLimitMinutes());
        breakdown.put("others1", num(priceDoc.getOthers1()));
        breakdown.put("others2", num(priceDoc.getOthers2()));
        breakdown.put("others3", num(priceDoc.getOthers3()));

        double miscellaneousCharge = kilometerPrice
                + num(priceDoc.getPriceSurge())
                + num(priceDoc.getInsurance())
                + num(priceDoc.getOthers1())
                + num(priceDoc.getOthers2())
                + num(priceDoc.getOthers3())
                + num(priceDoc.getPeakTimeCharge());

        double baseTotal = baseFare + driverAllowance + tollTax + miscellaneousCharge;

        double total;
        if (promo != null && !promo.isNull() && !promo.isEmpty()) {
            double discountAmount;
            String discountType = promo.path("discountType").asText("");
            double discountValue = promo.path("discountValue").asDouble(0);
            if ("percentage".equals(discountType)) {
                discountAmount = fix2((baseTotal * discountValue) / 100);
            } else {
                discountAmount = fix2(discountValue);
            }
            discountAmount = Math.min(discountAmount, baseTotal);
            total = baseTotal - discountAmount;
            breakdown.put("promo", discountAmount);
            breakdown.put("promoAmt", discountAmount);
        } else {
            total = baseTotal;
        }
        breakdown.put("total", total);

        double adminCommission = (total * commissionPercent) / 100;
        double subTotal = round2(total + adminCommission);
        breakdown.put("subTotal", subTotal);

        double gstAmount = (subTotal * gstPercent) / 100;
        breakdown.put("gstAmount", gstAmount);
        breakdown.put("grandTotal", round2(subTotal + gstAmount));
        breakdown.put("commission", round2(adminCommission));

        return breakdown;
    }

    private static double num(BigDecimal v) {
        return v == null ? 0d : v.doubleValue();
    }

    private static double num(Integer v) {
        return v == null ? 0d : v.doubleValue();
    }

    /** JS Number(x.toFixed(2)). */
    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** helpers/ride.commonFunction.js parseWithFix. */
    private static double fix2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
