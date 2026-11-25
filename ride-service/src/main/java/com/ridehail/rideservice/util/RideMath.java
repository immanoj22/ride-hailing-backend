package com.ridehail.rideservice.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Direct port of the small numeric helpers in helpers/ride.commonFunction.js
 * (parseWithFix/sectomin/calculateminutes/diffMinutes/getDaysBetweenDates) used throughout
 * ride.controller.js/ride.service.js's fare and cancellation-charge math.
 */
public final class RideMath {

    private RideMath() {
    }

    /** ride.commonFunction.js parseWithFix(str, 2) - round-half-up to 2 decimals. */
    public static BigDecimal fix2(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal fix2(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static double num(BigDecimal value) {
        return value == null ? 0d : value.doubleValue();
    }

    /** ride.commonFunction.js sectomin(seconds) = Math.floor(seconds / 60). */
    public static int sectomin(int seconds) {
        return Math.floorDiv(seconds, 60);
    }

    /** ride.commonFunction.js secToMin(secs) = Math.ceil(secs / 60). */
    public static int secToMinCeil(int seconds) {
        return (int) Math.ceil(seconds / 60.0);
    }

    /** ride.commonFunction.js calculateminutes(date1, date2) - absolute rounded minute diff. */
    public static long calculateMinutesAbs(long epochMillis1, long epochMillis2) {
        double diff = (epochMillis1 - epochMillis2) / 1000.0;
        diff /= 60.0;
        return Math.round(Math.abs(diff));
    }

    /** ride.commonFunction.js diffMinutes(from, to) = Math.floor((to - from) / 60000). */
    public static long diffMinutes(OffsetDateTime from, OffsetDateTime to) {
        if (from == null || to == null) {
            return 0;
        }
        long diffMs = ChronoUnit.MILLIS.between(from, to);
        return Math.floorDiv(diffMs, 60000L);
    }

    /** ride.commonFunction.js getDaysBetweenDates - ceil((end - start) / dayMs). */
    public static long getDaysBetweenDates(OffsetDateTime start, OffsetDateTime end) {
        if (start == null || end == null) {
            return 0;
        }
        long diffMs = ChronoUnit.MILLIS.between(start, end);
        return (long) Math.ceil(diffMs / (24.0 * 60 * 60 * 1000));
    }
}
