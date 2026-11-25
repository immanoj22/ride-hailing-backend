package com.ridehail.rideservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.common.security.InternalServiceClient;
import com.ridehail.rideservice.client.RideAxios;
import com.ridehail.rideservice.constants.RideConstants;
import com.ridehail.rideservice.entity.Ride;
import com.ridehail.rideservice.entity.RideProcess;
import com.ridehail.rideservice.entity.StarRating;
import com.ridehail.rideservice.entity.VehicleType;
import com.ridehail.rideservice.entity.VehicleVariant;
import com.ridehail.rideservice.repository.RideRepository;
import com.ridehail.rideservice.repository.StarRatingRepository;
import com.ridehail.rideservice.repository.VehicleTypeRepository;
import com.ridehail.rideservice.repository.VehicleVariantRepository;
import com.ridehail.rideservice.util.RideMapper;

/**
 * Port of the ratings-and-ride-summaries bucket of ride.controller.js:
 * get_rides_summary, get_user_ride_summary, get_driver_ride_summary, get_driver_rides,
 * get_active_ride_by_driver, get_pending_ride_by_driver, getLatestRideByUser, getRidesByUser,
 * getUsersRideList. Mongo aggregation pipelines are reproduced as in-memory reductions over the
 * matching rows (the fare breakdown lives in the free-form totalPrice JSONB blob).
 */
@Service
@Transactional(readOnly = true)
public class RideSummaryService {

    private static final List<String> CANCELLED_4 =
            List.of("cancelled", "cancel", "driver_cancel", "user_cancel");
    private static final List<String> ONGOING_SUMMARY =
            List.of("start", "assigned", "accept", "dropoff");
    private static final List<String> USER_CANCELLED_5 =
            List.of("cancel", "cancelled", "driver_cancel", "user_cancel", "cancelbyuser");
    private static final List<String> DRIVER_ONGOING =
            List.of("assigned", "accept", "start", "dropoff");
    private static final List<String> DRIVER_EARNINGS_CANCEL = List.of(
            "cancel", "cancelled", "driver_cancel", "user_cancel", "cancelbydriver",
            "cancelbyuser", "cancelbydriveronride", "cancelbyuseronride");

    private final RideRepository rideRepository;
    private final StarRatingRepository starRatingRepository;
    private final VehicleVariantRepository vehicleVariantRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final RideAxios rideAxios;
    private final InternalServiceClient internalServiceClient;

    public RideSummaryService(RideRepository rideRepository, StarRatingRepository starRatingRepository,
            VehicleVariantRepository vehicleVariantRepository, VehicleTypeRepository vehicleTypeRepository,
            RideAxios rideAxios, InternalServiceClient internalServiceClient) {
        this.rideRepository = rideRepository;
        this.starRatingRepository = starRatingRepository;
        this.vehicleVariantRepository = vehicleVariantRepository;
        this.vehicleTypeRepository = vehicleTypeRepository;
        this.rideAxios = rideAxios;
        this.internalServiceClient = internalServiceClient;
    }

    /* ============================ get_rides_summary ============================ */

    public Map<String, Object> getRidesSummary(String fromDate, String toDate, String rideType, String status) {
        Specification<Ride> spec = (root, q, cb) -> cb.isFalse(root.get("deleted"));
        if (notBlank(rideType)) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("rideType"), rideType));
        }
        if (notBlank(status)) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        if (notBlank(fromDate) && notBlank(toDate)) {
            OffsetDateTime from = OffsetDateTime.parse(fromDate);
            OffsetDateTime to = OffsetDateTime.parse(toDate);
            spec = spec.and((root, q, cb) -> cb.between(root.get("createdAt"), from, to));
        }
        List<Ride> rides = rideRepository.findAll(spec);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime startOfToday = now.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime weekAgo = now.minusDays(7);
        OffsetDateTime monthAgo = now.minusMonths(1);

        Map<String, Object> rideSummary;
        if (rides.isEmpty()) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("totalRides", 0);
            summary.put("completed", 0);
            summary.put("cancelled", 0);
            summary.put("ongoing", 0);
            summary.put("outstation", 0);
            summary.put("local", 0);
            Map<String, Object> revenue = new LinkedHashMap<>();
            revenue.put("total", 0);
            revenue.put("adminCommission", 0);
            rideSummary = new LinkedHashMap<>();
            rideSummary.put("summary", summary);
            rideSummary.put("revenue", revenue);
        } else {
            long total = rides.size();
            long completed = rides.stream().filter(r -> "endtrip".equals(r.getStatus())).count();
            long cancelled = rides.stream().filter(r -> CANCELLED_4.contains(r.getStatus())).count();
            long ongoing = rides.stream().filter(r -> ONGOING_SUMMARY.contains(r.getStatus())).count();
            long outstation = rides.stream().filter(r -> "outstation".equals(r.getRideType())).count();
            long local = rides.stream().filter(r -> "local".equals(r.getRideType())).count();
            double totalRevenue = rides.stream().mapToDouble(r -> num(r.getTotalPrice(), "grandTotal")).sum();
            double adminCommission = rides.stream().mapToDouble(r -> num(r.getTotalPrice(), "commission")).sum();
            double todayE = rides.stream().filter(r -> !r.getCreatedAt().isBefore(startOfToday))
                    .mapToDouble(r -> num(r.getTotalPrice(), "grandTotal")).sum();
            double weeklyE = rides.stream().filter(r -> !r.getCreatedAt().isBefore(weekAgo))
                    .mapToDouble(r -> num(r.getTotalPrice(), "grandTotal")).sum();
            double monthlyE = rides.stream().filter(r -> !r.getCreatedAt().isBefore(monthAgo))
                    .mapToDouble(r -> num(r.getTotalPrice(), "grandTotal")).sum();

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("totalRides", total);
            summary.put("completed", completed);
            summary.put("cancelled", cancelled);
            summary.put("ongoing", ongoing);
            summary.put("outstation", outstation);
            summary.put("local", local);
            Map<String, Object> revenue = new LinkedHashMap<>();
            revenue.put("total", round2(totalRevenue));
            revenue.put("adminCommission", round2(adminCommission));
            revenue.put("today", round2(todayE));
            revenue.put("weekly", round2(weeklyE));
            revenue.put("monthly", round2(monthlyE));
            rideSummary = new LinkedHashMap<>();
            rideSummary.put("summary", summary);
            rideSummary.put("revenue", revenue);
        }

        Map<String, Object> vehicleSummary = new LinkedHashMap<>();
        vehicleSummary.put("totalVehicles", vehicleVariantRepository.countByDeletedFalse());
        vehicleSummary.put("localVehicles", vehicleVariantRepository.countByDeletedFalseAndLocalTrue());
        vehicleSummary.put("outstationVehicles", vehicleVariantRepository.countByDeletedFalseAndOutstationTrue());
        vehicleSummary.put("totalVehicleTypes", vehicleTypeRepository.countByDeletedFalse());

        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("rides", rideSummary);
        inner.put("vehicles", vehicleSummary);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("data", inner);
        return data;
    }

    /* ========================= get_user_ride_summary ========================= */

    public Map<String, Object> getUserRideSummary(String userIdStr, int page, int limit, String search) {
        if (!notBlank(userIdStr)) {
            throw new ApiException(StatusCode.BAD_REQUEST, "User id is required");
        }
        UUID userId = uuid(userIdStr);
        boolean hasSearch = notBlank(search) && notBlank(search.trim());
        final String needle = hasSearch ? search.trim().toLowerCase() : null;

        // Preserve legacy behaviour: when a search term is present the (userId OR driverId) filter
        // is overwritten by the text filter (match.$or reassignment in the original).
        Specification<Ride> spec = (root, q, cb) -> cb.isFalse(root.get("deleted"));
        if (hasSearch) {
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("bookingNumber")), "%" + needle + "%"),
                    cb.like(cb.lower(root.get("rideType")), "%" + needle + "%")));
        } else {
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.equal(root.get("userId"), userId),
                    cb.equal(root.get("driverId"), userId)));
        }

        List<Ride> all = rideRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));

        int from = Math.max(0, (page - 1) * limit);
        int to = Math.min(all.size(), from + limit);
        List<Ride> pageRides = from >= all.size() ? List.of() : all.subList(from, to);

        List<UUID> rideIds = pageRides.stream().map(Ride::getId).toList();
        Map<UUID, List<StarRating>> ratingsByRide = new HashMap<>();
        if (!rideIds.isEmpty()) {
            for (StarRating sr : starRatingRepository.findByRideIdInAndDeletedFalse(rideIds)) {
                ratingsByRide.computeIfAbsent(sr.getRideId(), k -> new ArrayList<>()).add(sr);
            }
        }

        List<Map<String, Object>> rides = new ArrayList<>();
        for (Ride r : pageRides) {
            List<StarRating> rs = ratingsByRide.getOrDefault(r.getId(), List.of());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("_id", r.getId());
            row.put("bookingNumber", r.getBookingNumber());
            row.put("rideType", r.getRideType());
            row.put("status", r.getStatus());
            row.put("km", r.getKm());
            row.put("time", r.getRideTimeSeconds());
            row.put("totalAmount", node(r.getTotalPrice(), "grandTotal"));
            row.put("pickup", r.getLocationPickup());
            row.put("dropoff", r.getLocationDropoff());
            row.put("createdAt", r.getCreatedAt());
            row.put("userRating", ratingObj(rs, "user"));
            row.put("driverRating", ratingObj(rs, "driver"));
            rides.add(row);
        }

        long totalRides = all.size();
        long completed = all.stream().filter(r -> "endtrip".equals(r.getStatus())).count();
        long cancelled = all.stream().filter(r -> USER_CANCELLED_5.contains(r.getStatus())).count();
        double totalSpent = all.stream().filter(r -> "endtrip".equals(r.getStatus()))
                .mapToDouble(r -> num(r.getTotalPrice(), "grandTotal")).sum();
        double totalCancelSpent = all.stream().filter(r -> USER_CANCELLED_5.contains(r.getStatus()))
                .mapToDouble(r -> r.getCancelAmount() == null ? 0d : r.getCancelAmount().doubleValue()).sum();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRides", totalRides);
        summary.put("completed", completed);
        summary.put("cancelled", cancelled);
        summary.put("totalSpent", totalSpent);
        summary.put("totalCancelSpent", totalCancelSpent);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", summary);
        out.put("rides", rides);
        return out;
    }

    private Map<String, Object> ratingObj(List<StarRating> ratings, String fromType) {
        return ratings.stream().filter(r -> fromType.equals(r.getFromType())).findFirst()
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("star", r.getStarRating());
                    m.put("reason", r.getReason());
                    return m;
                }).orElse(null);
    }

    /* ======================== get_driver_ride_summary ======================== */

    public Map<String, Object> getDriverRideSummary(String driverIdStr, boolean summaryOnly, int page, int limit,
            String search) {
        if (!notBlank(driverIdStr)) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Driver id is required");
        }
        UUID driverId = uuid(driverIdStr);
        boolean hasSearch = notBlank(search) && notBlank(search.trim());
        final String needle = hasSearch ? search.trim().toLowerCase() : null;

        Specification<Ride> spec = (root, q, cb) -> cb.and(
                cb.isFalse(root.get("deleted")),
                cb.equal(root.get("driverId"), driverId));
        if (hasSearch) {
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("bookingNumber")), "%" + needle + "%"),
                    cb.like(cb.lower(root.get("paymentMethod")), "%" + needle + "%")));
        }

        List<Ride> all = rideRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));

        List<Map<String, Object>> ridesList = new ArrayList<>();
        long totalRidesCount = all.size();
        if (!summaryOnly) {
            int from = Math.max(0, (page - 1) * limit);
            int to = Math.min(all.size(), from + limit);
            List<Ride> pageRides = from >= all.size() ? List.of() : all.subList(from, to);
            for (Ride r : pageRides) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("_id", r.getId());
                row.put("bookingNumber", r.getBookingNumber());
                row.put("rideType", r.getRideType());
                row.put("status", r.getStatus());
                row.put("km", r.getKm());
                row.put("time", r.getRideTimeSeconds());
                row.put("totalAmount", node(r.getTotalPrice(), "grandTotal"));
                row.put("pickup", r.getLocationPickup());
                row.put("dropoff", r.getLocationDropoff());
                row.put("createdAt", r.getCreatedAt());
                row.put("city", null); // legacy projects ride.city - no such column in the frozen schema
                row.put("paymentMethod", r.getPaymentMethod());
                row.put("driverEarnings", driverEarnings(r));
                row.put("adminCommission", nodeOr0(r.getTotalPrice(), "commission"));
                ridesList.add(row);
            }
        }

        List<Map<String, Object>> summaryArr = new ArrayList<>();
        if (!all.isEmpty()) {
            long totalRides = all.size();
            long completedRides = all.stream().filter(r -> "endtrip".equals(r.getStatus())).count();
            long cancelledRides = all.stream().filter(r -> CANCELLED_4.contains(r.getStatus())).count();
            long ongoingRides = all.stream().filter(r -> DRIVER_ONGOING.contains(r.getStatus())).count();
            double totalKm = all.stream().mapToDouble(r -> r.getKm() == null ? 0d : r.getKm().doubleValue()).sum();
            long totalRideTime = all.stream().mapToLong(r -> r.getRideTimeSeconds() == null ? 0 : r.getRideTimeSeconds()).sum();
            double totalRevenue = all.stream().mapToDouble(r -> num(r.getTotalPrice(), "grandTotal")).sum();
            double driverEarnings = all.stream().mapToDouble(this::driverEarnings).sum();

            Map<String, Object> ridesObj = new LinkedHashMap<>();
            ridesObj.put("total", totalRides);
            ridesObj.put("completed", completedRides);
            ridesObj.put("cancelled", cancelledRides);
            ridesObj.put("ongoing", ongoingRides);
            Map<String, Object> distance = new LinkedHashMap<>();
            distance.put("totalKm", round2(totalKm));
            Map<String, Object> duration = new LinkedHashMap<>();
            duration.put("totalSeconds", totalRideTime);
            Map<String, Object> revenue = new LinkedHashMap<>();
            revenue.put("total", round2(totalRevenue));
            revenue.put("driverEarnings", round2(driverEarnings));
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("rides", ridesObj);
            s.put("distance", distance);
            s.put("duration", duration);
            s.put("revenue", revenue);
            summaryArr.add(s);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", summaryArr);
        if (!summaryOnly) {
            Map<String, Object> rides = new LinkedHashMap<>();
            rides.put("list", ridesList);
            rides.put("total", totalRidesCount);
            rides.put("page", page);
            rides.put("limit", limit);
            out.put("rides", rides);
        }
        return out;
    }

    private double driverEarnings(Ride r) {
        if (r.getDriverPayment() != null && !r.getDriverPayment().isEmpty()) {
            return r.getDriverPayment().stream()
                    .mapToDouble(p -> p.getAmount() == null ? 0d : p.getAmount().doubleValue()).sum();
        }
        boolean cancelled = DRIVER_EARNINGS_CANCEL.contains(r.getStatus());
        List<String> processStatuses = r.getProcess() == null ? List.of()
                : r.getProcess().stream().map(RideProcess::getStatus).toList();
        if (cancelled && !processStatuses.contains("startride") && !processStatuses.contains("start")) {
            return 0d;
        }
        return num(r.getTotalPrice(), "grandTotal") - num(r.getTotalPrice(), "commission");
    }

    /* ============================ get_driver_rides ============================ */

    public List<Map<String, Object>> getDriverRides(String driverIdStr, String status) {
        if (!notBlank(driverIdStr)) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Driver ID is required");
        }
        UUID driverId = uuid(driverIdStr);
        Map<String, List<String>> statusList = Map.of(
                "active", List.of("assigned", "accept", "start", "pickup", "dropoff"),
                "completed", List.of("endtrip", "completepayment"),
                "cancelled", List.of("cancel", "driver_cancel", "user_cancel"));

        List<Ride> rides;
        if (statusList.containsKey(status)) {
            rides = rideRepository.findByDriverIdAndStatusInOrderByCreatedAtDesc(driverId, statusList.get(status));
        } else {
            rides = rideRepository.findByDriverIdOrderByCreatedAtDesc(driverId);
        }

        if (rides.isEmpty()) {
            throw new ApiException(StatusCode.NOT_FOUND, "No rides found");
        }

        Map<UUID, VehicleVariant> variantMap = new HashMap<>();
        for (VehicleVariant v : vehicleVariantRepository.findAllById(
                rides.stream().map(Ride::getVehicleVariantId).filter(java.util.Objects::nonNull).distinct().toList())) {
            variantMap.put(v.getId(), v);
        }

        List<String> userIds = rides.stream().map(Ride::getUserId).filter(java.util.Objects::nonNull)
                .map(UUID::toString).distinct().toList();
        Map<String, JsonNode> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            JsonNode resp = rideAxios.getUsersByIds(Map.of("userIds", userIds));
            JsonNode arr = resp != null && resp.path("status").asBoolean(false) ? resp.path("data") : resp;
            if (arr != null && arr.isArray()) {
                for (JsonNode u : arr) {
                    String id = u.path("_id").asText(null);
                    if (id != null) {
                        userMap.put(id, u);
                    }
                }
            }
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Ride r : rides) {
            Map<String, Object> m = RideMapper.toLegacyMap(r);
            VehicleVariant v = variantMap.get(r.getVehicleVariantId());
            m.put("vehicleVariantId", v == null ? null : variantMap(v));
            Optional<StarRating> rating = (r.getDriverId() != null && r.getUserId() != null)
                    ? starRatingRepository.findByRideIdAndDriverIdAndUserIdAndFromTypeAndDeletedFalse(
                            r.getId(), r.getDriverId(), r.getUserId(), "driver")
                    : Optional.empty();
            m.put("rating", rating.map(StarRating::getStarRating).orElse(null));
            m.put("feedback", rating.map(StarRating::getReason).orElse(""));
            m.put("user", r.getUserId() == null ? null : userMap.get(r.getUserId().toString()));
            out.add(m);
        }
        return out;
    }

    private Map<String, Object> variantMap(VehicleVariant v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", v.getId());
        m.put("vehicleTypeId", v.getVehicleTypeId());
        m.put("type", v.getType());
        m.put("label", v.getLabel());
        m.put("description", v.getDescription());
        m.put("image", v.getImage());
        m.put("isLocal", v.isLocal());
        m.put("isOutstation", v.isOutstation());
        m.put("seats", v.getSeats());
        return m;
    }

    /* ======================= get_active_ride_by_driver ======================= */

    public Map<String, Object> getActiveRideByDriver(String driverIdStr) {
        if (!notBlank(driverIdStr)) {
            throw new ApiException(StatusCode.BAD_REQUEST, "driverId is required");
        }
        UUID driverId = uuid(driverIdStr);
        Optional<Ride> active = rideRepository.findFirstByDriverIdAndStatusInOrderByCreatedAtDesc(
                driverId, RideConstants.ACTIVE_RIDE_STATUSES);
        if (active.isEmpty()) {
            return null;
        }
        Ride ride = active.get();
        Map<String, Object> rideData = RideMapper.toLegacyMap(ride);

        // getRideDataById(id, "driver"): userType == "driver" -> do NOT fetch driver; fetch user + admin.
        if (ride.getUserId() != null) {
            JsonNode userRes = rideAxios.getUserById(ride.getUserId());
            JsonNode userData = userRes == null ? null : userRes.path("data").path("user");
            if (userData != null && !userData.isMissingNode() && !userData.isNull()) {
                rideData.put("userId", userData);
            }
        }
        JsonNode adminRes = rideAxios.getAdmin();
        JsonNode adminData = adminRes == null ? null : adminRes.path("data");
        if (adminData != null && !adminData.isMissingNode() && !adminData.isNull()) {
            rideData.put("adminId", adminData);
        }
        return rideData;
    }

    /* ======================= get_pending_ride_by_driver ======================= */

    public Map<String, Object> getPendingRideByDriver(String driverIdStr) {
        if (!notBlank(driverIdStr)) {
            throw new ApiException(StatusCode.BAD_REQUEST, "driverId is required");
        }
        UUID driverId = uuid(driverIdStr);
        Optional<Ride> pendingOpt = rideRepository.findPendingByRequestedDriver(driverId);
        if (pendingOpt.isEmpty()) {
            return null;
        }
        Ride pending = pendingOpt.get();

        JsonNode user = null;
        if (pending.getUserId() != null) {
            JsonNode userRes = rideAxios.getUserById(pending.getUserId());
            if (userRes != null) {
                JsonNode d = userRes.path("data");
                if (d.has("data")) {
                    user = d.path("data");
                } else if (!d.isMissingNode() && !d.isNull()) {
                    user = d;
                }
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rideId", pending.getId());
        payload.put("bookingNumber", pending.getBookingNumber());
        payload.put("pickup", RideMapper.latLng(pending.getPickupLat(), pending.getPickupLng()));
        payload.put("drop", RideMapper.latLng(pending.getDropLat(), pending.getDropLng()));
        payload.put("vehicleTypeId", pending.getVehicleTypeId());
        payload.put("vehicleVariantId", pending.getVehicleVariantId());
        Map<String, Object> location = new LinkedHashMap<>();
        location.put("pickup", pending.getLocationPickup());
        location.put("dropoff", pending.getLocationDropoff());
        location.put("pickupDistrict", pending.getLocationPickupDistrict());
        location.put("dropoffDistrict", pending.getLocationDropoffDistrict());
        payload.put("location", location);

        if (user != null) {
            JsonNode u = user.path("user");
            Map<String, Object> um = new LinkedHashMap<>();
            um.put("id", u.path("_id").isMissingNode() ? null : u.path("_id"));
            um.put("name", u.path("firstname").asText("") + " " + u.path("lastname").asText(""));
            um.put("email", u.path("email").isMissingNode() ? null : u.path("email"));
            um.put("profile", u.path("profile").isMissingNode() ? null : u.path("profile"));
            payload.put("user", um);
        }
        return payload;
    }

    /* ========================== getLatestRideByUser ========================== */

    /** @return null when no matching ride (controller then answers 204). */
    public Map<String, Object> getLatestRideByUser(String userIdStr) {
        if (!notBlank(userIdStr)) {
            throw new ApiException(StatusCode.BAD_REQUEST, "User ID is required");
        }
        UUID userId = uuid(userIdStr);
        List<String> allowed = List.of("assigned", "accept", "startride", "dropoff", "pickup", "completepayment");
        Specification<Ride> spec = (root, q, cb) -> cb.and(
                cb.equal(root.get("userId"), userId),
                cb.isFalse(root.get("deleted")),
                cb.or(
                        root.get("status").in(allowed),
                        cb.and(cb.equal(root.get("rideType"), "local"), cb.equal(root.get("status"), "create"))));
        List<Ride> rides = rideRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (rides.isEmpty()) {
            return null;
        }
        Ride r = rides.get(0);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", r.getId());
        m.put("status", r.getStatus());
        List<Map<String, Object>> process = new ArrayList<>();
        if (r.getProcess() != null) {
            for (RideProcess p : r.getProcess()) {
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("_id", p.getId());
                pm.put("status", p.getStatus());
                pm.put("createdAt", p.getCreatedAt());
                pm.put("updatedAt", p.getUpdatedAt());
                process.add(pm);
            }
        }
        m.put("process", process);
        return m;
    }

    /* ============================ getRidesByUser ============================ */

    /** @return empty list when no rides (controller answers 400, preserving legacy). */
    public List<Map<String, Object>> getRidesByUser(String userIdStr, int page, int limit, String active) {
        if (!notBlank(userIdStr)) {
            throw new ApiException(StatusCode.BAD_REQUEST, "User ID is required");
        }
        UUID userId = uuid(userIdStr);
        Specification<Ride> spec = (root, q, cb) -> cb.and(
                cb.equal(root.get("userId"), userId),
                cb.isFalse(root.get("deleted")));
        if ("local".equals(active) || "outstation".equals(active)) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("rideType"), active));
        }
        List<Ride> all = rideRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));
        int from = Math.max(0, (page - 1) * limit);
        int to = Math.min(all.size(), from + limit);
        List<Ride> pageRides = from >= all.size() ? List.of() : all.subList(from, to);
        if (pageRides.isEmpty()) {
            return List.of();
        }

        Map<UUID, VehicleType> typeMap = new HashMap<>();
        for (VehicleType t : vehicleTypeRepository.findAllById(
                pageRides.stream().map(Ride::getVehicleTypeId).filter(java.util.Objects::nonNull).distinct().toList())) {
            typeMap.put(t.getId(), t);
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Ride r : pageRides) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("_id", r.getId());
            m.put("bookingNumber", r.getBookingNumber());
            Map<String, Object> location = new LinkedHashMap<>();
            location.put("pickup", r.getLocationPickup());
            location.put("dropoff", r.getLocationDropoff());
            location.put("pickupDistrict", r.getLocationPickupDistrict());
            location.put("dropoffDistrict", r.getLocationDropoffDistrict());
            m.put("location", location);
            m.put("rideType", r.getRideType());
            m.put("status", r.getStatus());
            m.put("totalPrice", r.getTotalPrice());
            m.put("bookingDate", r.getBookingDate());
            m.put("km", r.getKm());
            m.put("time", r.getRideTimeSeconds());
            VehicleType t = typeMap.get(r.getVehicleTypeId());
            if (t != null) {
                Map<String, Object> tm = new LinkedHashMap<>();
                tm.put("_id", t.getId());
                tm.put("type", t.getType());
                tm.put("label", t.getLabel());
                tm.put("image", t.getImage());
                m.put("vehicleTypeId", tm);
            } else {
                m.put("vehicleTypeId", r.getVehicleTypeId());
            }
            m.put("outstationType", r.getOutstationTripType());
            out.add(m);
        }
        return out;
    }

    /* ============================ getUsersRideList ============================ */

    public JsonNode getUsersRideList(Integer page, Integer limit, String search) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("sort", Map.of("createdAt", -1));
        if (page != null && limit != null && page > 0 && limit > 0) {
            options.put("skip", (page - 1) * limit);
            options.put("limit", limit);
        }
        Map<String, Object> body = new HashMap<>();
        body.put("options", options);
        if (search != null) {
            body.put("search", search);
        }
        return rideAxios.getUsersByIds(body);
    }

    /* ================================ helpers ================================ */

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static UUID uuid(String v) {
        try {
            return UUID.fromString(v);
        } catch (IllegalArgumentException e) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Invalid id: " + v);
        }
    }

    private static double num(JsonNode obj, String field) {
        if (obj == null) {
            return 0d;
        }
        JsonNode n = obj.get(field);
        if (n == null || n.isNull()) {
            return 0d;
        }
        if (n.isNumber()) {
            return n.asDouble();
        }
        try {
            return Double.parseDouble(n.asText().trim());
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    private static JsonNode node(JsonNode obj, String field) {
        return obj == null ? null : obj.get(field);
    }

    private static Object nodeOr0(JsonNode obj, String field) {
        JsonNode n = node(obj, field);
        return (n == null || n.isNull()) ? 0 : n;
    }

    private static BigDecimal round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }
}
