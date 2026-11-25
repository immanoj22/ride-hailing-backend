package com.ridehail.rideservice.util;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.ridehail.rideservice.entity.Price;

/**
 * Direct port of ride.service.js calculateFinalOutstationFare({priceDoc, existingPrice, extraKm,
 * extraTime, waitingMinutes, userId}) - the ride-end fare finalisation (docs/PRICING_FLOW.md 3.2).
 * The original's quirk (grandTotal = subTotal, GST added only once) is preserved verbatim.
 */
public final class OutstationFinalFareCalculator {

    private OutstationFinalFareCalculator() {
    }

    private static double d(BigDecimal v) {
        return v == null ? 0d : v.doubleValue();
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    public static Map<String, Object> calculateFinal(Price priceDoc,
                                                     JsonNode existingPrice,
                                                     double extraKm,
                                                     double extraTime,
                                                     double waitingMinutes,
                                                     double pendingAmount) {
        double extraKmFare = d(priceDoc.getOutstationExtraKmFare());
        double extraMinuteFare = d(priceDoc.getOutstationExtraMinuteFare());
        double waitingCharge = d(priceDoc.getOutstationWaitingCharge());

        double extraAmount = extraKm * extraKmFare
                + extraTime * extraMinuteFare
                + waitingMinutes * waitingCharge;

        double existingTotal = existingPrice == null ? 0d : existingPrice.path("total").asDouble(0);
        double baseFareBeforeGST = existingTotal + extraAmount;

        double gstPercent = existingPrice == null ? 0d : existingPrice.path("gstPercent").asDouble(0);
        double gstAmount = (baseFareBeforeGST * gstPercent) / 100;
        double subTotal = baseFareBeforeGST + gstAmount;
        double grandTotal = subTotal; // preserved: JS sets grandTotal = subTotal

        double finalGrandTotal = round2(grandTotal + pendingAmount);

        Map<String, Object> out = new LinkedHashMap<>();
        if (existingPrice != null && existingPrice.isObject()) {
            existingPrice.fields().forEachRemaining(e -> out.put(e.getKey(), e.getValue()));
        }
        out.put("extraKm", extraKm);
        out.put("extraTime", extraTime);
        out.put("waitingMinutes", waitingMinutes);
        out.put("extraKmAmount", extraKm * extraKmFare);
        out.put("extraTimeAmount", extraTime * extraMinuteFare);
        out.put("waitingAmount", waitingMinutes * waitingCharge);
        out.put("total", baseFareBeforeGST);
        out.put("gstAmount", gstAmount);
        out.put("subTotal", subTotal);
        out.put("cancelAmountOnRide", pendingAmount);
        out.put("grandTotal", finalGrandTotal);
        return out;
    }
}
