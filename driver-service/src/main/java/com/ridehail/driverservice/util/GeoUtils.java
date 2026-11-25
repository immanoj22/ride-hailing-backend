package com.ridehail.driverservice.util;

/**
 * Application-side replacement for the Mongo {@code $near}/2dsphere geo query used by the legacy
 * nearby_drivers/nearby_outstation_drivers - see V1__init.sql's note on {@code current_latitude}/
 * {@code current_longitude}: no PostGIS extension is provisioned, so a bounding-box pre-filter
 * (pushed down to SQL) plus an exact Haversine distance filter/sort (done here, in memory) is used
 * instead of a true geography column.
 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_METERS = 6371000;

    private GeoUtils() {
    }

    public static double[] latRange(double lat, double radiusMeters) {
        double delta = Math.toDegrees(radiusMeters / EARTH_RADIUS_METERS);
        return new double[] { lat - delta, lat + delta };
    }

    public static double[] lngRange(double lat, double lng, double radiusMeters) {
        double delta = Math.toDegrees(radiusMeters / (EARTH_RADIUS_METERS * Math.cos(Math.toRadians(lat))));
        return new double[] { lng - delta, lng + delta };
    }

    /** Haversine great-circle distance in meters. */
    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }
}
