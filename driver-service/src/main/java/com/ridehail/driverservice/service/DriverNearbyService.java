package com.ridehail.driverservice.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.common.security.InternalServiceClient;
import com.ridehail.driverservice.dto.AcceptOutstationRideRequest;
import com.ridehail.driverservice.dto.GetOutstationRidesRequest;
import com.ridehail.driverservice.dto.NearbyDriversRequest;
import com.ridehail.driverservice.dto.NearbyOutstationDriversRequest;
import com.ridehail.driverservice.dto.OutstationFeedsRequest;
import com.ridehail.driverservice.dto.TriggerSosRequest;
import com.ridehail.driverservice.entity.Driver;
import com.ridehail.driverservice.repository.DriverRepository;

/**
 * Direct port of driver.controller.js's nearby_drivers, nearby_outstation_drivers,
 * acceptOutstationRide, get_outstation_rides, outstation_feeds, get_ride, trigger_sos and
 * get_support_ticket_chats (plus the driverLocationService.js findNearbyDrivers approach, ported
 * without PostGIS: a lat/lng bounding box specification narrows the SQL scan, then an in-memory
 * Haversine pass mirrors the original $near / $maxDistance semantics).
 *
 * <p>{@code nearby_drivers} is called directly by ride-service's create_ride /
 * nearbyDriversAvailable flows (POST {@code /nearby-drivers}) - see {@code RideAxios.getNearByDrivers}
 * and {@code OutstationRideService.nearbyDriversAvailable} in ride-service. Both callers ultimately
 * only need each returned driver's {@code _id}; OutstationRideService additionally reads
 * {@code firstName}, {@code lastName}, {@code socketId}, {@code currentLocation}, {@code fcmToken}
 * and {@code vehicleType} off each element - so the response list preserves those exact field
 * names from the original Mongoose projection ("_id socketId firstName lastName profileImage
 * mobile currentLocation vehicleType fcmToken").
 */
@Service
public class DriverNearbyService {

    private static final double EARTH_RADIUS_METERS = 6371000d;
    /** ~1 degree of latitude in meters - used to build a generous bounding box before the exact
     *  Haversine pass, mirroring the original geo $near query's rough pre-filter. */
    private static final double METERS_PER_DEGREE_LAT = 111_320d;

    private final DriverRepository driverRepository;
    private final InternalServiceClient internalServiceClient;
    private final String rideUrl;
    private final String helperdeskUrl;
    private final String socketUrl;

    public DriverNearbyService(
            DriverRepository driverRepository,
            InternalServiceClient internalServiceClient,
            @Value("${ridehail.services.ride-url}") String rideUrl,
            @Value("${ridehail.services.helperdesk-url}") String helperdeskUrl,
            @Value("${ridehail.services.socket-url}") String socketUrl) {
        this.driverRepository = driverRepository;
        this.internalServiceClient = internalServiceClient;
        this.rideUrl = rideUrl;
        this.helperdeskUrl = helperdeskUrl;
        this.socketUrl = socketUrl;
    }

    // ------------------------------------------------------------------ nearby_drivers

    public List<Map<String, Object>> nearbyDrivers(NearbyDriversRequest req) {
        Double lat = req.getPickup() == null ? null : req.getPickup().getLat();
        Double lng = req.getPickup() == null ? null : req.getPickup().getLng();
        double radiusMeters = req.getMaxDistance() != null ? req.getMaxDistance()
                : (req.getRadiusMeters() != null ? req.getRadiusMeters() : 5000d);
        int limit = req.getLimit() != null ? req.getLimit() : 10;

        UUID selfId = parseUuidOrNull(req.get_id());
        UUID vehicleTypeId = parseUuidOrNull(req.getVehicleTypeId());
        UUID vehicleVariantId = parseUuidOrNull(req.getVehicleVariantId());

        Specification<Driver> spec = (root, query, cb) -> cb.and(
                cb.isFalse(root.get("deleted")),
                cb.isFalse(root.get("onRide")),
                cb.equal(root.get("driverStatus"), "online"),
                cb.isTrue(root.get("kycCompleted")),
                cb.equal(root.get("kycStatus"), "approved"));

        if (selfId != null) {
            UUID finalSelfId = selfId;
            spec = spec.and((root, query, cb) -> cb.equal(root.get("id"), finalSelfId));
        } else {
            if (vehicleTypeId != null) {
                UUID finalVehicleTypeId = vehicleTypeId;
                spec = spec.and((root, query, cb) -> cb.equal(root.get("vehicleType"), finalVehicleTypeId));
            }
            if (vehicleVariantId != null) {
                UUID finalVehicleVariantId = vehicleVariantId;
                spec = spec.and((root, query, cb) -> cb.equal(root.get("vehicleVariant"), finalVehicleVariantId));
            }
        }

        if (lat != null && lng != null) {
            double latDelta = radiusMeters / METERS_PER_DEGREE_LAT;
            double lngDelta = radiusMeters / (METERS_PER_DEGREE_LAT * Math.max(0.1, Math.cos(Math.toRadians(lat))));
            double minLat = lat - latDelta;
            double maxLat = lat + latDelta;
            double minLng = lng - lngDelta;
            double maxLng = lng + lngDelta;
            spec = spec.and((root, query, cb) -> cb.and(
                    cb.isNotNull(root.get("currentLatitude")),
                    cb.isNotNull(root.get("currentLongitude")),
                    cb.between(root.get("currentLatitude"), minLat, maxLat),
                    cb.between(root.get("currentLongitude"), minLng, maxLng)));
        }

        List<Driver> candidates = driverRepository.findAll(spec);

        List<Map<String, Object>> nearby = new ArrayList<>();
        for (Driver driver : candidates) {
            if (lat != null && lng != null) {
                if (driver.getCurrentLatitude() == null || driver.getCurrentLongitude() == null) {
                    continue;
                }
                double distance = haversineMeters(lat, lng, driver.getCurrentLatitude(), driver.getCurrentLongitude());
                if (distance > radiusMeters) {
                    continue;
                }
            }
            nearby.add(toNearbyDriverView(driver));
        }

        if (lat != null && lng != null) {
            nearby.sort(Comparator.comparingDouble(m -> distanceOf(m, lat, lng)));
        }

        if (nearby.size() > limit) {
            nearby = new ArrayList<>(nearby.subList(0, limit));
        }

        if (nearby.isEmpty()) {
            throw new ApiException(StatusCode.NOT_FOUND, "Nearby drivers not found");
        }

        return nearby;
    }

    private double distanceOf(Map<String, Object> driverView, double lat, double lng) {
        Object loc = driverView.get("currentLocation");
        if (!(loc instanceof Map<?, ?> locMap)) {
            return Double.MAX_VALUE;
        }
        Object dLat = locMap.get("lat");
        Object dLng = locMap.get("lng");
        if (!(dLat instanceof Double) || !(dLng instanceof Double)) {
            return Double.MAX_VALUE;
        }
        return haversineMeters(lat, lng, (Double) dLat, (Double) dLng);
    }

    /** Mirrors the original "_id socketId firstName lastName profileImage mobile currentLocation
     *  vehicleType fcmToken" Mongoose projection. */
    private Map<String, Object> toNearbyDriverView(Driver driver) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("_id", driver.getId() == null ? null : driver.getId().toString());
        view.put("socketId", driver.getSocketId());
        view.put("firstName", driver.getFirstName());
        view.put("lastName", driver.getLastName());
        view.put("profileImage", driver.getProfileImage());
        view.put("mobile", driver.getMobile());
        Map<String, Object> currentLocation = new LinkedHashMap<>();
        currentLocation.put("lat", driver.getCurrentLatitude());
        currentLocation.put("lng", driver.getCurrentLongitude());
        view.put("currentLocation", currentLocation);
        view.put("vehicleType", driver.getVehicleType() == null ? null : driver.getVehicleType().toString());
        view.put("fcmToken", driver.getFcmToken());
        return view;
    }

    // ------------------------------------------------------------------ nearby_outstation_drivers

    public List<Map<String, Object>> nearbyOutstationDrivers(NearbyOutstationDriversRequest req) {
        if (req.getPickupDistrict() == null || req.getPickupDistrict().isBlank()
                || req.getDropoffDistrict() == null || req.getDropoffDistrict().isBlank()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Pickup location is required");
        }

        UUID vehicleTypeId = parseUuidOrNull(req.getVehicleTypeId());
        UUID vehicleVariantId = parseUuidOrNull(req.getVehicleVariantId());
        String pickup = req.getPickupDistrict().trim().toLowerCase();
        String drop = req.getDropoffDistrict().trim().toLowerCase();
        Pattern pickupPattern = Pattern.compile(Pattern.quote(pickup), Pattern.CASE_INSENSITIVE);
        Pattern dropPattern = Pattern.compile(Pattern.quote(drop), Pattern.CASE_INSENSITIVE);

        Specification<Driver> spec = (root, query, cb) -> cb.and(
                cb.isFalse(root.get("deleted")),
                cb.isTrue(root.get("kycCompleted")),
                cb.equal(root.get("kycStatus"), "approved"));
        if (vehicleTypeId != null) {
            UUID finalVehicleTypeId = vehicleTypeId;
            spec = spec.and((root, query, cb) -> cb.equal(root.get("vehicleType"), finalVehicleTypeId));
        }
        if (vehicleVariantId != null) {
            UUID finalVehicleVariantId = vehicleVariantId;
            spec = spec.and((root, query, cb) -> cb.equal(root.get("vehicleVariant"), finalVehicleVariantId));
        }

        List<Driver> candidates = driverRepository.findAll(spec);
        List<Map<String, Object>> drivers = new ArrayList<>();
        for (Driver driver : candidates) {
            if (!preferredLocationsMatch(driver, pickupPattern, dropPattern)) {
                continue;
            }
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("_id", driver.getId() == null ? null : driver.getId().toString());
            view.put("socketId", driver.getSocketId());
            view.put("name", (driver.getFirstName() == null ? "" : driver.getFirstName())
                    + " " + (driver.getLastName() == null ? "" : driver.getLastName()));
            view.put("mobile", driver.getMobile());
            view.put("preferredLocations", driver.getPreferredLocations());
            view.put("vehicleType", driver.getVehicleType() == null ? null : driver.getVehicleType().toString());
            view.put("fcmToken", driver.getFcmToken());
            drivers.add(view);
        }

        if (drivers.isEmpty()) {
            throw new ApiException(StatusCode.NOT_FOUND, "No outstation drivers found");
        }
        return drivers;
    }

    private boolean preferredLocationsMatch(Driver driver, Pattern pickupPattern, Pattern dropPattern) {
        JsonNode locations = driver.getPreferredLocations();
        if (locations == null || !(locations instanceof ArrayNode) || locations.isEmpty()) {
            return false;
        }
        for (JsonNode loc : locations) {
            String text = loc.isTextual() ? loc.asText() : loc.toString();
            if (pickupPattern.matcher(text).find() || dropPattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ acceptOutstationRide

    public Map<String, Object> acceptOutstationRide(String driverId, AcceptOutstationRideRequest req) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("driverId", driverId);
        payload.put("rideId", req.getRideId());

        JsonNode resp = internalServiceClient.post(rideUrl + "/accept-outstation-ride", payload);
        if (!internalServiceClient.statusTrue(resp)) {
            throw new ApiException(StatusCode.BAD_GATEWAY, "Ride service not responding");
        }
        return Map.of(
                "message", resp.path("message").asText("Outstation ride accepted successfully"),
                "data", dataAsMap(resp));
    }

    // ------------------------------------------------------------------ get_outstation_rides

    public Map<String, Object> getOutstationRides(String driverId, GetOutstationRidesRequest req) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("driverId", driverId);
        payload.put("page", req.getPage() != null ? req.getPage() : 1);
        payload.put("limit", req.getLimit() != null ? req.getLimit() : 10);
        if (req.getStatus() != null && !"create".equals(req.getStatus())) {
            payload.put("status", req.getStatus());
        }

        JsonNode resp = internalServiceClient.post(rideUrl + "/outstation-rides", payload);
        if (!internalServiceClient.statusTrue(resp)) {
            throw new ApiException(StatusCode.INTERNAL_SERVER_ERROR,
                    resp.path("message").asText("Failed to fetch outstation rides"));
        }
        return Map.of(
                "message", resp.path("message").asText("Outstation rides fetched successfully"),
                "data", dataAsMap(resp));
    }

    // ------------------------------------------------------------------ outstation_feeds

    public Map<String, Object> outstationFeeds(String driverId, OutstationFeedsRequest req) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("driverId", driverId);
        payload.put("seeAll", req.getSeeAll() != null ? req.getSeeAll() : Boolean.FALSE);
        payload.put("page", req.getPage() != null ? req.getPage() : 1);
        payload.put("limit", req.getLimit() != null ? req.getLimit() : 10);
        if (req.getTripType() != null) {
            payload.put("tripType", req.getTripType());
        }
        if (req.getCity() != null) {
            payload.put("selectedCity", req.getCity());
        }

        JsonNode resp = internalServiceClient.post(rideUrl + "/outstation-feeds", payload);
        if (!internalServiceClient.statusTrue(resp)) {
            throw new ApiException(StatusCode.INTERNAL_SERVER_ERROR,
                    resp.path("message").asText("Failed to fetch outstation feed"));
        }
        return Map.of(
                "message", resp.path("message").asText("Outstation feed fetched successfully"),
                "data", dataAsMap(resp));
    }

    // ------------------------------------------------------------------ get_ride

    public Map<String, Object> getRide(String driverId, String rideId) {
        if (driverId == null || driverId.isBlank()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Not authorized");
        }
        if (rideId == null || rideId.isBlank()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "rideId is required");
        }

        JsonNode resp = internalServiceClient.get(rideUrl + "/" + rideId);
        if (!internalServiceClient.statusTrue(resp)) {
            throw new ApiException(StatusCode.NOT_FOUND, resp.path("message").asText("Ride not found"));
        }
        return Map.of(
                "message", resp.path("message").asText("Ride fetched successfully"),
                "data", dataAsMap(resp));
    }

    // ------------------------------------------------------------------ trigger_sos

    public Map<String, Object> triggerSos(String driverId, TriggerSosRequest req) {
        if (req.getRideId() == null || req.getRideId().isBlank()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Ride ID is required");
        }
        Double lat = req.getLocation() == null ? null : req.getLocation().getLat();
        Double lng = req.getLocation() == null ? null : req.getLocation().getLng();
        if (lat == null || lng == null || lat == 0 || lng == 0) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Location is required");
        }

        Map<String, Object> location = new LinkedHashMap<>();
        location.put("lat", lat);
        location.put("lng", lng);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", driverId);
        payload.put("rideId", req.getRideId());
        payload.put("userType", "driver");
        payload.put("location", location);

        JsonNode resp = internalServiceClient.post(helperdeskUrl + "/sos/trigger", payload);
        if (!internalServiceClient.statusTrue(resp)) {
            int statusCode = resp.path("statusCode").asInt(StatusCode.INTERNAL_SERVER_ERROR);
            throw new ApiException(statusCode, resp.path("message").asText("Failed to trigger SOS"));
        }
        return Map.of("data", dataAsMap(resp));
    }

    // ------------------------------------------------------------------ get_support_ticket_chats

    public Map<String, Object> getSupportTicketChats(String driverId, String ticketId) {
        if (driverId == null || driverId.isBlank()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Not authorized");
        }

        JsonNode resp = internalServiceClient.get(socketUrl + "/get-chat-by-ticketid/" + ticketId);
        if (!internalServiceClient.statusTrue(resp)) {
            int code = resp.path("code").asInt(StatusCode.NOT_FOUND);
            throw new ApiException(code, resp.path("message").asText("Failed to fetch support ticket chats"));
        }
        return Map.of(
                "message", resp.path("message").asText("Support ticket chats fetched successfully"),
                "data", dataAsMap(resp));
    }

    // ------------------------------------------------------------------ helpers

    /** Safely unwraps the {@code data} field of a downstream service response, tolerating a
     *  missing/null node (converted to an empty map) and both object- and array-shaped payloads. */
    private Object dataAsMap(JsonNode resp) {
        JsonNode data = resp.path("data");
        if (data.isMissingNode() || data.isNull()) {
            return Map.of();
        }
        if (data.isArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonNode element : data) {
                list.add(internalServiceClient.asMap(element));
            }
            return list;
        }
        return internalServiceClient.asMap(data);
    }

    private UUID parseUuidOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
