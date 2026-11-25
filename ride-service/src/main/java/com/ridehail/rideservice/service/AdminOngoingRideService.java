package com.ridehail.rideservice.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.rideservice.client.RideAxios;
import com.ridehail.rideservice.constants.RideConstants;
import com.ridehail.rideservice.entity.Ride;
import com.ridehail.rideservice.entity.RideGps;
import com.ridehail.rideservice.entity.VehicleType;
import com.ridehail.rideservice.entity.VehicleVariant;
import com.ridehail.rideservice.repository.RideGpsRepository;
import com.ridehail.rideservice.repository.RideRepository;
import com.ridehail.rideservice.repository.VehicleTypeRepository;
import com.ridehail.rideservice.repository.VehicleVariantRepository;

/** Ports ride.controller.js: get_ongoing_rides_admin, get_ongoing_ride_gps. */
@Service
@Transactional(readOnly = true)
public class AdminOngoingRideService {

    private final RideRepository rideRepository;
    private final RideGpsRepository rideGpsRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final VehicleVariantRepository vehicleVariantRepository;
    private final RideAxios axios;

    public AdminOngoingRideService(RideRepository rideRepository, RideGpsRepository rideGpsRepository,
            VehicleTypeRepository vehicleTypeRepository, VehicleVariantRepository vehicleVariantRepository,
            RideAxios axios) {
        this.rideRepository = rideRepository;
        this.rideGpsRepository = rideGpsRepository;
        this.vehicleTypeRepository = vehicleTypeRepository;
        this.vehicleVariantRepository = vehicleVariantRepository;
        this.axios = axios;
    }

    public Map<String, Object> getOngoingRidesAdmin(String rideType, String rideId, int page, int limit,
            boolean includeGPS, boolean includeProfiles, String sort) {

        UUID rideUuid = null;
        if (rideId != null && !rideId.isBlank()) {
            try {
                rideUuid = UUID.fromString(rideId);
            } catch (Exception e) {
                throw new ApiException(StatusCode.BAD_REQUEST, "Invalid rideId");
            }
        }
        final UUID rideIdFilter = rideUuid;
        final String rideTypeFilter = (rideType != null && !rideType.equals("all")) ? rideType : null;

        Specification<Ride> spec = (root, q, cb) -> {
            var preds = new ArrayList<jakarta.persistence.criteria.Predicate>();
            preds.add(cb.isNotNull(root.get("driverId")));
            preds.add(cb.isFalse(root.get("deleted")));
            preds.add(root.get("status").in(RideConstants.ACTIVE_RIDE_STATUSES));
            if (rideTypeFilter != null) {
                preds.add(cb.equal(root.get("rideType"), rideTypeFilter));
            }
            if (rideIdFilter != null) {
                preds.add(cb.equal(root.get("id"), rideIdFilter));
            }
            return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Sort sortObj = "oldest".equals(sort)
                ? Sort.by(Sort.Direction.ASC, "createdAt") : Sort.by(Sort.Direction.DESC, "createdAt");
        long total = rideRepository.count(spec);
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), limit, sortObj);
        List<Ride> rides = rideRepository.findAll(spec, pageable).getContent();

        Map<UUID, VehicleType> typeMap = vehicleTypeRepository.findAllById(
                        rides.stream().map(Ride::getVehicleTypeId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(VehicleType::getId, v -> v));
        Map<UUID, VehicleVariant> variantMap = vehicleVariantRepository.findAllById(
                        rides.stream().map(Ride::getVehicleVariantId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(VehicleVariant::getId, v -> v));

        Map<UUID, Map<String, Object>> gpsMap = new LinkedHashMap<>();
        if (includeGPS) {
            for (Ride r : rides) {
                RideGps latest = rideGpsRepository.findFirstByRideIdOrderByRecordedAtDescCreatedAtDesc(r.getId()).orElse(null);
                gpsMap.put(r.getId(), latest == null ? null : gpsPoint(latest));
            }
        }

        Map<String, JsonNode> userMap = new LinkedHashMap<>();
        Map<String, JsonNode> driverMap = new LinkedHashMap<>();
        if (includeProfiles && !rides.isEmpty()) {
            List<String> userIds = rides.stream().map(Ride::getUserId).filter(Objects::nonNull)
                    .map(UUID::toString).distinct().toList();
            List<String> driverIds = rides.stream().map(Ride::getDriverId).filter(Objects::nonNull)
                    .map(UUID::toString).distinct().toList();
            if (!userIds.isEmpty()) {
                collectProfiles(axios.getUsersByIds(userIds), userMap);
            }
            if (!driverIds.isEmpty()) {
                collectProfiles(axios.getDriversByIds(Map.of("driverIds", driverIds)), driverMap);
            }
        }

        List<Map<String, Object>> ridesWithMeta = new ArrayList<>();
        for (Ride r : rides) {
            Map<String, Object> m = rideSummary(r, typeMap.get(r.getVehicleTypeId()), variantMap.get(r.getVehicleVariantId()));
            if (includeGPS) {
                m.put("latestGPS", gpsMap.get(r.getId()));
            }
            if (includeProfiles && r.getUserId() != null) {
                m.put("userProfile", userMap.get(r.getUserId().toString()));
            }
            if (includeProfiles && r.getDriverId() != null) {
                m.put("driverProfile", driverMap.get(r.getDriverId().toString()));
            }
            ridesWithMeta.add(m);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("page", page);
        data.put("limit", limit);
        data.put("rides", ridesWithMeta);
        return data;
    }

    public Map<String, Object> getOngoingRideGps(String rideId, int limit) {
        UUID rideUuid;
        try {
            rideUuid = UUID.fromString(rideId);
        } catch (Exception e) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Invalid rideId");
        }

        Ride ride = rideRepository.findById(rideUuid)
                .filter(r -> !r.isDeleted() && RideConstants.ACTIVE_RIDE_STATUSES.contains(r.getStatus()))
                .orElseThrow(() -> new ApiException(StatusCode.NOT_FOUND, "Ongoing ride not found"));

        Pageable pageable = PageRequest.of(0, Math.max(limit, 1));
        List<RideGps> points = rideGpsRepository.findByRideIdOrderByRecordedAtDescCreatedAtDesc(rideUuid, pageable);

        Map<String, Object> rideDto = new LinkedHashMap<>();
        rideDto.put("_id", ride.getId());
        rideDto.put("bookingNumber", ride.getBookingNumber());
        rideDto.put("rideType", ride.getRideType());
        rideDto.put("status", ride.getStatus());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ride", rideDto);
        data.put("gpsPoints", points.stream().map(this::gpsFull).toList());
        return data;
    }

    private void collectProfiles(JsonNode resp, Map<String, JsonNode> target) {
        if (resp == null || !resp.path("status").asBoolean(false)) {
            return;
        }
        JsonNode arr = resp.path("data");
        if (arr.isArray()) {
            for (JsonNode node : arr) {
                String id = node.path("_id").asText(null);
                if (id != null) {
                    target.put(id, node);
                }
            }
        }
    }

    private Map<String, Object> gpsPoint(RideGps g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("latitude", g.getLatitude());
        m.put("longitude", g.getLongitude());
        m.put("speed", g.getSpeed());
        m.put("heading", g.getHeading());
        m.put("accuracy", g.getAccuracy());
        m.put("recordedAt", g.getRecordedAt());
        m.put("driverId", g.getDriverId());
        return m;
    }

    private Map<String, Object> gpsFull(RideGps g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", g.getId());
        m.put("rideId", g.getRideId());
        m.put("driverId", g.getDriverId());
        m.put("userId", g.getUserId());
        m.put("coordinates", Map.of(
                "latitude", g.getLatitude() == null ? 0 : g.getLatitude(),
                "longitude", g.getLongitude() == null ? 0 : g.getLongitude()));
        m.put("speed", g.getSpeed());
        m.put("heading", g.getHeading());
        m.put("accuracy", g.getAccuracy());
        m.put("recordedAt", g.getRecordedAt());
        m.put("createdAt", g.getCreatedAt());
        return m;
    }

    private Map<String, Object> rideSummary(Ride r, VehicleType type, VehicleVariant variant) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", r.getId());
        m.put("bookingNumber", r.getBookingNumber());
        m.put("rideType", r.getRideType());
        m.put("status", r.getStatus());
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
        m.put("vehicleTypeId", type == null ? r.getVehicleTypeId()
                : Map.of("_id", type.getId(), "type", type.getType(), "label", type.getLabel()));
        m.put("vehicleVariantId", variant == null ? r.getVehicleVariantId() : Map.of(
                "_id", variant.getId(),
                "vehicleTypeId", variant.getVehicleTypeId(),
                "type", variant.getType(),
                "label", variant.getLabel(),
                "isOutstation", variant.isOutstation(),
                "isLocal", variant.isLocal()));
        m.put("createdAt", r.getCreatedAt());
        m.put("updatedAt", r.getUpdatedAt());
        m.put("paymentMethod", r.getPaymentMethod());
        return m;
    }

    private Map<String, Object> latLng(Double lat, Double lng) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lat", lat);
        m.put("lng", lng);
        return m;
    }
}
