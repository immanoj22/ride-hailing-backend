package com.ridehail.rideservice.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.rideservice.client.RideAxios;
import com.ridehail.rideservice.constants.RideConstants;
import com.ridehail.rideservice.dto.AcceptOutstationRideRequest;
import com.ridehail.rideservice.dto.EndOutstationRideRequest;
import com.ridehail.rideservice.dto.GetOutstationRidesRequest;
import com.ridehail.rideservice.dto.NearbyDriversRequest;
import com.ridehail.rideservice.dto.OutstationFeedRequest;
import com.ridehail.rideservice.dto.OutstationInfoDto;
import com.ridehail.rideservice.dto.OutstationRideRequest;
import com.ridehail.rideservice.dto.RentalSlotsRequest;
import com.ridehail.rideservice.entity.Price;
import com.ridehail.rideservice.entity.PriceOnewayPackage;
import com.ridehail.rideservice.entity.PriceRentalPackage;
import com.ridehail.rideservice.entity.Ride;
import com.ridehail.rideservice.entity.RideGps;
import com.ridehail.rideservice.entity.RideProcess;
import com.ridehail.rideservice.entity.VehicleType;
import com.ridehail.rideservice.entity.VehicleVariant;
import com.ridehail.rideservice.realtime.RideNotificationPublisher;
import com.ridehail.rideservice.repository.PriceOnewayPackageRepository;
import com.ridehail.rideservice.repository.PriceRentalPackageRepository;
import com.ridehail.rideservice.repository.PriceRepository;
import com.ridehail.rideservice.repository.RideGpsRepository;
import com.ridehail.rideservice.repository.RideRepository;
import com.ridehail.rideservice.repository.StarRatingRepository;
import com.ridehail.rideservice.repository.VehicleTypeRepository;
import com.ridehail.rideservice.repository.VehicleVariantRepository;
import com.ridehail.rideservice.util.OutstationFareCalculator;
import com.ridehail.rideservice.util.OutstationFinalFareCalculator;

/**
 * Business logic for the "outstation-rides-and-pricing-lookup" bucket of ride.controller.js:
 * create_outstation_ride, accept_outstation_ride, update_outstation_ride, end_outstation_ride,
 * nearby_driversAvailable, get_outstation_rides, get_outstation_ride_list, outstation_feeds,
 * getRentalSlots, get_ride_payment_modes, getPriceDetailsById.
 */
@Service
public class OutstationRideService {

    private static final Logger log = LoggerFactory.getLogger(OutstationRideService.class);

    private final RideRepository rideRepository;
    private final PriceRepository priceRepository;
    private final PriceOnewayPackageRepository onewayPackageRepository;
    private final PriceRentalPackageRepository rentalPackageRepository;
    private final VehicleVariantRepository vehicleVariantRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final RideGpsRepository rideGpsRepository;
    private final StarRatingRepository starRatingRepository;
    private final RideAxios rideAxios;
    private final RideNotificationPublisher notificationPublisher;
    private final ObjectMapper mapper = new ObjectMapper();

    public OutstationRideService(RideRepository rideRepository,
                                 PriceRepository priceRepository,
                                 PriceOnewayPackageRepository onewayPackageRepository,
                                 PriceRentalPackageRepository rentalPackageRepository,
                                 VehicleVariantRepository vehicleVariantRepository,
                                 VehicleTypeRepository vehicleTypeRepository,
                                 RideGpsRepository rideGpsRepository,
                                 StarRatingRepository starRatingRepository,
                                 RideAxios rideAxios,
                                 RideNotificationPublisher notificationPublisher) {
        this.rideRepository = rideRepository;
        this.priceRepository = priceRepository;
        this.onewayPackageRepository = onewayPackageRepository;
        this.rentalPackageRepository = rentalPackageRepository;
        this.vehicleVariantRepository = vehicleVariantRepository;
        this.vehicleTypeRepository = vehicleTypeRepository;
        this.rideGpsRepository = rideGpsRepository;
        this.starRatingRepository = starRatingRepository;
        this.rideAxios = rideAxios;
        this.notificationPublisher = notificationPublisher;
    }

    // ------------------------------------------------------------------ helpers

    private static UUID uuid(String v, String errMsg) {
        try {
            return UUID.fromString(v);
        } catch (Exception e) {
            throw new ApiException(StatusCode.BAD_REQUEST, errMsg);
        }
    }

    private static UUID uuidOrNull(String v) {
        try {
            return v == null ? null : UUID.fromString(v);
        } catch (Exception e) {
            return null;
        }
    }

    private static int secToMinCeil(double secs) {
        return (int) Math.ceil(secs / 60.0);
    }

    private static double asDouble(Double v) {
        return v == null ? 0d : v;
    }

    private OffsetDateTime parseDate(String v) {
        if (v == null || v.isBlank()) {
            return OffsetDateTime.now();
        }
        try {
            return OffsetDateTime.parse(v);
        } catch (Exception e) {
            try {
                return java.time.Instant.parse(v).atOffset(java.time.ZoneOffset.UTC);
            } catch (Exception ex) {
                return OffsetDateTime.now();
            }
        }
    }

    /** helpers/ride.commonFunction.js generateBookingNumber(). */
    private String generateBookingNumber() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String timestampStr = timestamp.length() > 6 ? timestamp.substring(3, timestamp.length() - 3) : timestamp;
        long rideCount = rideRepository.count();
        return "KR" + timestampStr + rideCount;
    }

    private JsonNode couponSnapshot(String promoId) {
        JsonNode coupon = rideAxios.getCouponById(promoId);
        JsonNode data = coupon == null ? null : coupon.path("data");
        if (coupon == null || !coupon.path("status").asBoolean(false) || data == null || data.isMissingNode()
                || data.isNull() || (data.isObject() && data.isEmpty())) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Invalid coupon code");
        }
        ObjectNode snap = mapper.createObjectNode();
        snap.set("_id", data.path("_id"));
        snap.set("code", data.path("code"));
        snap.set("discountType", data.path("discountType"));
        snap.set("discountValue", data.path("discountValue"));
        snap.set("tripType", data.path("tripType"));
        return snap;
    }

    private Price resolveOutstationPrice(VehicleVariant variant) {
        if (variant.getOutstationPriceId() == null) {
            return null;
        }
        return priceRepository.findById(variant.getOutstationPriceId()).orElse(null);
    }

    private JsonNode toJson(Object o) {
        return mapper.valueToTree(o);
    }

    // ------------------------------------------------------------------ create_outstation_ride

    @Transactional
    public Map<String, Object> createOutstationRide(OutstationRideRequest req, String authUserId) {
        String userIdStr = req.getUserId() != null ? req.getUserId() : authUserId;
        UUID userId = uuid(userIdStr, "Invalid userId");

        if (!"outstation".equals(req.getRideType())) {
            throw new ApiException(StatusCode.BAD_REQUEST, "rideType must be 'outstation' for this endpoint");
        }
        UUID vehicleTypeId = uuid(req.getVehicleTypeId(), "Invalid vehicleTypeId");
        UUID vehicleVariantId = uuid(req.getVehicleVariantId(), "Invalid vehicleVariantId");

        try {
            JsonNode userRes = rideAxios.getUserById(userId);
            if (userRes == null || !userRes.path("status").asBoolean(false)) {
                throw new ApiException(StatusCode.NOT_FOUND, "User not found");
            }
        } catch (ApiException ae) {
            throw ae;
        } catch (Exception e) {
            log.warn("User service lookup failed: {}", e.getMessage());
        }

        VehicleVariant variant = vehicleVariantRepository.findById(vehicleVariantId)
                .filter(v -> !v.isDeleted())
                .orElseThrow(() -> new ApiException(StatusCode.NOT_FOUND, "Vehicle variant not found"));

        Price priceDoc = resolveOutstationPrice(variant);
        if (priceDoc == null) {
            throw new ApiException(StatusCode.NOT_FOUND,
                    "Pricing not configured for outstation for selected vehicle");
        }

        OffsetDateTime bookingDate = parseDate(req.getBookingDate());
        OffsetDateTime endDate = req.getEndDate() == null ? null : parseDate(req.getEndDate());
        long days = 0;
        if (endDate != null) {
            days = (long) Math.ceil(
                    java.time.Duration.between(bookingDate, endDate).toMillis() / (24.0 * 60 * 60 * 1000));
        }

        OutstationInfoDto out = req.getOutstation();
        String tripType = out.getTripType();
        double kmEstimated = out.getKmEstimated() != null ? out.getKmEstimated()
                : (req.getKm() != null ? req.getKm() : 0d);
        double durationSeconds = out.getDurationEstimated() != null ? out.getDurationEstimated()
                : (req.getTime() != null ? req.getTime() : 0d);
        int durationEstimatedMin = secToMinCeil(durationSeconds);

        if ("rental".equals(tripType)) {
            if (out.getPackageId() == null) {
                throw new ApiException(StatusCode.BAD_REQUEST, "Invalid packageId");
            }
        } else if (!"oneway".equals(tripType) && !"round".equals(tripType)) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Invalid outstation trip type");
        }

        JsonNode promo = null;
        if (req.getPromo() != null && !req.getPromo().isBlank()) {
            promo = couponSnapshot(req.getPromo());
        }

        List<PriceOnewayPackage> oneway = onewayPackageRepository.findByPriceIdOrderByFromKmAsc(priceDoc.getId());
        List<PriceRentalPackage> rental = rentalPackageRepository.findByPriceId(priceDoc.getId());

        Map<String, Object> fareCalc = OutstationFareCalculator.calculate(
                priceDoc, oneway, rental, out.getPackageId(), tripType, kmEstimated, promo, days);
        if (fareCalc.containsKey("error")) {
            throw new ApiException(StatusCode.INTERNAL_SERVER_ERROR, String.valueOf(fareCalc.get("error")));
        }

        Ride ride = new Ride();
        ride.setUserId(userId);
        ride.setPickupLat(req.getPickup().path("lat").isNumber() ? req.getPickup().path("lat").asDouble() : null);
        ride.setPickupLng(req.getPickup().path("lng").isNumber() ? req.getPickup().path("lng").asDouble() : null);
        ride.setDropLat(req.getDrop().path("lat").isNumber() ? req.getDrop().path("lat").asDouble() : null);
        ride.setDropLng(req.getDrop().path("lng").isNumber() ? req.getDrop().path("lng").asDouble() : null);
        ride.setLocationPickup(req.getPickupAddress() == null ? "" : req.getPickupAddress());
        ride.setLocationDropoff(req.getDropAddress() == null ? "" : req.getDropAddress());
        ride.setLocationPickupDistrict(req.getPickupDistrict() == null ? "" : req.getPickupDistrict());
        ride.setLocationDropoffDistrict(req.getDropoffDistrict() == null ? "" : req.getDropoffDistrict());
        ride.setRideType("outstation");
        ride.setBookingNumber(generateBookingNumber());
        ride.setPromo(promo != null ? promo : mapper.createObjectNode());
        ride.setStatus("create");
        ride.setVehicleTypeId(vehicleTypeId);
        ride.setVehicleVariantId(vehicleVariantId);
        ride.setToll(Boolean.TRUE.equals(req.getIsToll()));
        ride.setCreatedBy(req.getCreatedBy() == null ? "user" : req.getCreatedBy());
        ride.setBookingDate(bookingDate);
        ride.setNotes(req.getNotes() == null ? "" : req.getNotes());
        ride.setExtraCarrier(Boolean.TRUE.equals(req.getExtraCarrier()));
        ride.setMultipleDrop(toJson(req.getMultipleDrop() == null ? List.of() : req.getMultipleDrop()));
        ride.setKm(BigDecimal.valueOf(req.getKm() == null ? 0d : req.getKm()));
        ride.setRideTimeSeconds((int) durationSeconds);
        ride.setEndDate(endDate);
        ride.setPriceId(priceDoc.getId());
        ride.setOutstationTripType(tripType);
        ride.setOutstationKmEstimated(BigDecimal.valueOf(kmEstimated));
        ride.setOutstationDurationEstimated(durationEstimatedMin);
        ride.setTotalPrice(toJson(fareCalc));

        ride.getProcess().add(RideProcess.builder().ride(ride).status("create").build());

        // outstation candidate drivers (utills/ride.axios.js getNearByOutstationDrivers)
        List<UUID> requestedDrivers = new ArrayList<>();
        List<JsonNode> driverDocs = new ArrayList<>();
        try {
            Map<String, Object> reqPayload = new LinkedHashMap<>();
            reqPayload.put("pickupDistrict", ride.getLocationPickupDistrict());
            reqPayload.put("dropoffDistrict", ride.getLocationDropoffDistrict());
            reqPayload.put("vehicleTypeId", vehicleTypeId.toString());
            reqPayload.put("vehicleVariantId", vehicleVariantId.toString());
            reqPayload.put("radiusMeters", 10000);
            JsonNode drivers = rideAxios.getNearByOutstationDrivers(reqPayload);
            JsonNode data = drivers == null ? null : drivers.path("data");
            if (data != null && data.isArray()) {
                for (JsonNode dn : data) {
                    driverDocs.add(dn);
                    UUID did = uuidOrNull(dn.path("_id").asText(null));
                    if (did != null) {
                        requestedDrivers.add(did);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("getNearByOutstationDrivers failed: {}", e.getMessage());
        }
        ride.setRequestedDrivers(requestedDrivers.toArray(new UUID[0]));

        Ride saved = rideRepository.save(ride);

        // consume referral discount (user-service)
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("userId", saved.getUserId().toString());
            body.put("rideId", saved.getId().toString());
            JsonNode ref = rideAxios.consumeReferralDiscount(body);
            if (ref != null && ref.path("success").asBoolean(false)
                    && ref.path("data").path("applied").asBoolean(false)) {
                double pct = ref.path("data").path("discountPercent").asDouble(0);
                ObjectNode tp = (ObjectNode) saved.getTotalPrice();
                double base = tp.has("total") ? tp.path("total").asDouble(0) : tp.path("grandTotal").asDouble(0);
                double referralDiscount = Math.round(Math.min(base, base * pct / 100) * 100.0) / 100.0;
                double newGrand = Math.max(0, Math.round(
                        (tp.path("grandTotal").asDouble(0) - referralDiscount) * 100.0) / 100.0);
                tp.put("referralDiscount", referralDiscount);
                tp.put("referralDiscountPercent", pct);
                tp.put("grandTotal", newGrand);
                saved.setTotalPrice(tp);
                saved = rideRepository.save(saved);
            }
        } catch (Exception e) {
            log.warn("consumeReferralDiscount failed: {}", e.getMessage());
        }

        // coupon usage (admin-service)
        if (promo != null) {
            try {
                Map<String, Object> couponPayload = new LinkedHashMap<>();
                couponPayload.put("userId", saved.getUserId().toString());
                couponPayload.put("rideId", saved.getId().toString());
                couponPayload.put("couponId", promo.path("_id").asText(null));
                couponPayload.put("discountAmount", saved.getTotalPrice().path("promo").asDouble(0));
                rideAxios.createCouponUsage(couponPayload);
            } catch (Exception e) {
                log.warn("createCouponUsage failed: {}", e.getMessage());
            }
        }

        // push notification (config/rabitmq.js sendNotification)
        if (!driverDocs.isEmpty()) {
            try {
                Map<String, Object> n = new LinkedHashMap<>();
                n.put("status", "create-outstation");
                Map<String, Object> android = new LinkedHashMap<>();
                android.put("id", saved.getId().toString());
                android.put("bookingNumber", saved.getBookingNumber());
                n.put("androidData", android);
                List<String> driverTokens = new ArrayList<>();
                List<String> driverIds = new ArrayList<>();
                for (JsonNode dn : driverDocs) {
                    driverTokens.add(dn.path("fcmToken").asText(null));
                    driverIds.add(dn.path("_id").asText(null));
                }
                n.put("driver", driverTokens);
                n.put("userId", saved.getUserId().toString());
                n.put("driverIds", driverIds);
                notificationPublisher.sendNotification(n);
            } catch (Exception e) {
                log.warn("sendNotification failed: {}", e.getMessage());
            }
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("rideId", saved.getId().toString());
        resp.put("bookingNumber", saved.getBookingNumber());
        resp.put("grandTotal", saved.getTotalPrice().path("grandTotal").asDouble(0));
        resp.put("ride", toRideView(saved, null, null));
        return resp;
    }

    // ------------------------------------------------------------------ accept_outstation_ride

    @Transactional
    public Map<String, Object> acceptOutstationRide(AcceptOutstationRideRequest req, String authDriverId) {
        UUID rideId = uuid(req.getRideId(), "Invalid rideId");
        String driverIdStr = authDriverId != null ? authDriverId : req.getDriverId();
        UUID driverId = uuid(driverIdStr, "Unauthorized driver");

        JsonNode driverRes = rideAxios.getDriverById(driverId);
        if (driverRes == null || !driverRes.path("status").asBoolean(false)) {
            throw new ApiException(StatusCode.NOT_FOUND, "Driver not found");
        }
        JsonNode driverData = driverRes.path("data");

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ApiException(StatusCode.NOT_FOUND, "Ride not found"));

        if (!"outstation".equals(ride.getRideType())) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Not an outstation ride");
        }
        if (!"create".equals(ride.getStatus())) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Ride already accepted or expired");
        }
        if (ride.getDriverId() != null) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Ride already assigned");
        }

        ride.setDriverId(driverId);
        ride.setStatus("assigned");
        ride.setRequestedDrivers(new UUID[0]);
        ride.getProcess().add(RideProcess.builder().ride(ride).status("assigned").build());
        Ride saved = rideRepository.save(ride);

        try {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("status", "outstationaccept");
            Map<String, Object> android = new LinkedHashMap<>();
            android.put("id", saved.getId().toString());
            android.put("bookingNumber", saved.getBookingNumber());
            android.put("triptype", "outstation");
            n.put("androidData", android);
            n.put("driver", driverData.path("fcmToken").asText(null));
            n.put("userId", saved.getUserId().toString());
            n.put("driverId", driverId.toString());
            JsonNode userRes = rideAxios.getUserById(saved.getUserId());
            if (userRes != null) {
                String fcm = userRes.path("data").path("user").path("fcm").asText(null);
                if (fcm == null) {
                    fcm = userRes.path("data").path("fcm").asText(null);
                }
                n.put("user", fcm);
            }
            notificationPublisher.sendNotification(n);
        } catch (Exception e) {
            log.warn("Notification publish failed: {}", e.getMessage());
        }

        return toRideView(saved, null, null);
    }

    // ------------------------------------------------------------------ update_outstation_ride

    @Transactional
    public Map<String, Object> updateOutstationRide(OutstationRideRequest req) {
        if (req.getRideId() == null) {
            throw new ApiException(StatusCode.BAD_REQUEST, "RideId is required");
        }
        UUID rideId = uuid(req.getRideId(), "Invalid rideId or userId");
        UUID userId = uuid(req.getUserId(), "Invalid rideId or userId");

        Ride ride = rideRepository
                .findByIdAndUserIdAndRideTypeAndStatusAndDeletedFalseAndDriverIdIsNull(
                        rideId, userId, "outstation", "create")
                .orElseThrow(() -> new ApiException(StatusCode.BAD_REQUEST, "Ride not editable"));

        UUID vehicleVariantId = uuid(req.getVehicleVariantId(), "Invalid vehicle pricing");
        VehicleVariant variant = vehicleVariantRepository.findById(vehicleVariantId)
                .filter(v -> !v.isDeleted())
                .orElseThrow(() -> new ApiException(StatusCode.NOT_FOUND, "Invalid vehicle pricing"));
        Price priceDoc = resolveOutstationPrice(variant);
        if (priceDoc == null) {
            throw new ApiException(StatusCode.NOT_FOUND, "Invalid vehicle pricing");
        }

        JsonNode promo = null;
        if (req.getPromo() != null && !req.getPromo().isBlank()) {
            promo = couponSnapshot(req.getPromo());
        }

        OutstationInfoDto out = req.getOutstation();
        String tripType = out.getTripType();
        double kmEstimated = out.getKmEstimated() != null ? out.getKmEstimated()
                : (req.getKm() != null ? req.getKm() : 0d);
        double durationSeconds = out.getDurationEstimated() != null ? out.getDurationEstimated()
                : (req.getTime() != null ? req.getTime() : 0d);
        int durationEstimatedMin = secToMinCeil(durationSeconds);

        List<PriceOnewayPackage> oneway = onewayPackageRepository.findByPriceIdOrderByFromKmAsc(priceDoc.getId());
        List<PriceRentalPackage> rental = rentalPackageRepository.findByPriceId(priceDoc.getId());
        Map<String, Object> fareCalc = OutstationFareCalculator.calculate(
                priceDoc, oneway, rental, out.getPackageId(), tripType, kmEstimated, promo, 0d);
        if (fareCalc.containsKey("error")) {
            throw new ApiException(StatusCode.INTERNAL_SERVER_ERROR, String.valueOf(fareCalc.get("error")));
        }

        List<UUID> requestedDrivers = new ArrayList<>();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("pickupDistrict", req.getPickupDistrict());
            body.put("dropoffDistrict", req.getDropoffDistrict());
            body.put("vehicleTypeId", req.getVehicleTypeId());
            body.put("vehicleVariantId", req.getVehicleVariantId());
            body.put("radiusMeters", 10000);
            JsonNode drivers = rideAxios.getNearByOutstationDrivers(body);
            JsonNode data = drivers == null ? null : drivers.path("data");
            if (data != null && data.isArray()) {
                for (JsonNode dn : data) {
                    UUID did = uuidOrNull(dn.path("_id").asText(null));
                    if (did != null) {
                        requestedDrivers.add(did);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Driver fetch failed: {}", e.getMessage());
        }

        ride.setPickupLat(req.getPickup().path("lat").isNumber() ? req.getPickup().path("lat").asDouble() : null);
        ride.setPickupLng(req.getPickup().path("lng").isNumber() ? req.getPickup().path("lng").asDouble() : null);
        ride.setDropLat(req.getDrop().path("lat").isNumber() ? req.getDrop().path("lat").asDouble() : null);
        ride.setDropLng(req.getDrop().path("lng").isNumber() ? req.getDrop().path("lng").asDouble() : null);
        ride.setLocationPickup(req.getPickupAddress() == null ? "" : req.getPickupAddress());
        ride.setLocationDropoff(req.getDropAddress() == null ? "" : req.getDropAddress());
        ride.setLocationPickupDistrict(req.getPickupDistrict() == null ? "" : req.getPickupDistrict());
        ride.setLocationDropoffDistrict(req.getDropoffDistrict() == null ? "" : req.getDropoffDistrict());
        ride.setVehicleTypeId(uuidOrNull(req.getVehicleTypeId()));
        ride.setVehicleVariantId(vehicleVariantId);
        ride.setNotes(req.getNotes() == null ? "" : req.getNotes());
        ride.setExtraCarrier(Boolean.TRUE.equals(req.getExtraCarrier()));
        ride.setMultipleDrop(toJson(req.getMultipleDrop() == null ? List.of() : req.getMultipleDrop()));
        ride.setBookingDate(parseDate(req.getBookingDate()));
        ride.setOutstationTripType(tripType);
        ride.setOutstationKmEstimated(BigDecimal.valueOf(kmEstimated));
        ride.setOutstationDurationEstimated(durationEstimatedMin);
        ride.setPromo(promo != null ? promo : mapper.createObjectNode());
        ride.setTotalPrice(toJson(fareCalc));
        ride.setRequestedDrivers(requestedDrivers.toArray(new UUID[0]));
        ride.setIgnoredDrivers(new UUID[0]);

        return toRideView(rideRepository.save(ride), null, null);
    }

    // ------------------------------------------------------------------ end_outstation_ride

    @Transactional
    public Map<String, Object> endOutstationRide(EndOutstationRideRequest req) {
        UUID rideId = uuid(req.getRideId(), "Invalid ids");
        UUID driverId = uuid(req.getDriverId(), "Invalid ids");
        double waitingMinutes = asDouble(req.getWaitingMinutes());

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ApiException(StatusCode.NOT_FOUND, "Ride not found"));

        if (!"outstation".equals(ride.getRideType())) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Invalid ride type");
        }
        if (ride.getDriverId() == null || !ride.getDriverId().equals(driverId)) {
            throw new ApiException(StatusCode.UNAUTHORIZED, "Unauthorized driver");
        }
        if ("endtrip".equals(ride.getStatus())) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Ride already ended");
        }

        List<RideGps> gpsPoints = rideGpsRepository
                .findByRideIdAndDriverIdAndUserIdOrderByRecordedAtAsc(rideId, driverId, ride.getUserId());

        double actualKm = 0d;
        for (int i = 1; i < gpsPoints.size(); i++) {
            actualKm += haversineMeters(
                    gpsPoints.get(i - 1).getLatitude(), gpsPoints.get(i - 1).getLongitude(),
                    gpsPoints.get(i).getLatitude(), gpsPoints.get(i).getLongitude()) / 1000.0;
        }
        actualKm = Math.round(actualKm * 100.0) / 100.0;

        RideProcess pickup = ride.getProcess().stream()
                .filter(p -> "pickup".equals(p.getStatus())).findFirst()
                .orElseThrow(() -> new ApiException(StatusCode.BAD_REQUEST, "Ride not started yet"));

        long startTime = pickup.getCreatedAt().toInstant().toEpochMilli();
        long endTime = System.currentTimeMillis();

        double estimatedKm = ride.getOutstationKmEstimated().doubleValue();
        double extraKm = Math.max(0, actualKm - estimatedKm);

        int actualDurationMin = (int) Math.ceil((endTime - startTime) / 60000.0);
        int estimatedDurationMin = ride.getOutstationDurationEstimated();
        int extraTime = Math.max(0, actualDurationMin - estimatedDurationMin);

        Price priceDoc = ride.getPriceId() == null ? null
                : priceRepository.findById(ride.getPriceId()).filter(p -> !p.isDeleted()).orElse(null);
        if (priceDoc == null) {
            throw new ApiException(StatusCode.NOT_FOUND, "Pricing not found");
        }

        BigDecimal pending = rideRepository.sumPendingCancelAmounts(ride.getUserId());
        Map<String, Object> finalPrice = OutstationFinalFareCalculator.calculateFinal(
                priceDoc, ride.getTotalPrice(), extraKm, extraTime, waitingMinutes,
                pending == null ? 0d : pending.doubleValue());

        double finalKm = ride.getKm().doubleValue() > actualKm ? ride.getKm().doubleValue() : actualKm;
        int finalDurationMin = ride.getRideTimeSeconds() > actualDurationMin
                ? ride.getRideTimeSeconds() : actualDurationMin;

        ride.setStatus("dropoff");
        ride.setKm(BigDecimal.valueOf(finalKm));
        ride.setRideTimeSeconds(finalDurationMin);
        ride.setTotalPrice(toJson(finalPrice));
        ride.setOutstationTotalKm(BigDecimal.valueOf(finalKm));
        ride.setOutstationExtraKm(BigDecimal.valueOf(extraKm));
        ride.setOutstationExtraTime(extraTime);
        ride.setOutstationTotalDuration(finalDurationMin);
        ride.getProcess().add(RideProcess.builder().ride(ride).status("dropoff").build());
        Ride saved = rideRepository.save(ride);

        try {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("status", "dropoff");
            notificationPublisher.sendNotification(n);
        } catch (Exception e) {
            log.warn("sendNotification failed: {}", e.getMessage());
        }

        return toRideView(saved, null, null);
    }

    private static double haversineMeters(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return 0d;
        }
        double r = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // ------------------------------------------------------------------ nearby_driversAvailable

    public List<Map<String, Object>> nearbyDriversAvailable(NearbyDriversRequest req) {
        double lng = req.getPickup().path("lng").asDouble();
        double lat = req.getPickup().path("lat").asDouble();

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("delete", false);
        query.put("onRide", false);
        query.put("pickup", req.getPickup());
        query.put("vehicleTypeId", req.getVehicleTypeId());
        Map<String, Object> geo = new LinkedHashMap<>();
        geo.put("type", "Point");
        geo.put("coordinates", List.of(lng, lat));
        query.put("point", geo);
        query.put("maxDistance", req.getRadiusMeters() == null ? 5000d : req.getRadiusMeters());
        if (req.getVehicleVariantId() != null) {
            query.put("vehicleVariantId", req.getVehicleVariantId());
        }

        JsonNode resp = rideAxios.getNearByDrivers(query);
        JsonNode data = resp == null ? null : resp.path("data");
        if (data == null || !data.isArray() || data.isEmpty()) {
            throw new ApiException(StatusCode.NOT_FOUND, "Nearby drivers not found");
        }

        java.util.Set<String> skip = new java.util.HashSet<>();
        if (req.getDriverSkip() != null) {
            skip.addAll(req.getDriverSkip());
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode driver : data) {
            String id = driver.path("_id").asText(null);
            if (id != null && skip.contains(id)) {
                continue;
            }
            String vehicleType = driver.path("vehicleType").asText(null);
            Price price = null;
            if (vehicleType != null) {
                UUID vt = uuidOrNull(vehicleType);
                if (vt != null) {
                    price = priceRepository.findByVehicleTypeIdAndDeletedFalse(vt).stream().findFirst().orElse(null);
                }
            }
            if (price == null) {
                out.add(mapper.convertValue(driver, Map.class));
                continue;
            }
            Map<String, Object> total = calculateLocalFare(price,
                    asDouble(req.getKm()), asDouble(req.getTime()));
            VehicleType vtEntity = price.getVehicleTypeId() == null ? null
                    : vehicleTypeRepository.findById(price.getVehicleTypeId()).orElse(null);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("fullname", driver.path("firstName").asText("") + "  " + driver.path("lastName").asText(""));
            row.put("socketId", driver.path("socketId").asText(null));
            row.put("currentLocation", driver.path("currentLocation"));
            row.put("fcmToken", driver.path("fcmToken").asText(null));
            Map<String, Object> vtype = new LinkedHashMap<>();
            vtype.put("vehicletype", vehicleType);
            vtype.put("vehiclemodel", vtEntity == null ? null : vtEntity.getLabel());
            vtype.put("image", vtEntity == null ? null : vtEntity.getImage());
            vtype.put("label", vtEntity == null ? null : vtEntity.getLabel());
            vtype.put("value", id);
            vtype.put("TotalPrice", total);
            row.put("vehicletype", vtype);
            out.add(row);
        }
        return out;
    }

    /** Port of ride.service.js calculateFare({ price, km, time }). */
    private Map<String, Object> calculateLocalFare(Price price, double km, double time) {
        double d = 0;
        double kilometerprice = round2(km * dv(price.getPricePerKm()));
        double minuteprice = round2((time / 60.0) * dv(price.getPricePerMin()));
        double baseprice = dv(price.getBasePrice());
        double peak = dv(price.getPeakTimeCharge());
        double insurance = dv(price.getInsurance());
        double o1 = dv(price.getOthers1()), o2 = dv(price.getOthers2()), o3 = dv(price.getOthers3());
        double total = round2(kilometerprice + minuteprice + d + baseprice + peak + insurance + o1 + o2 + o3 + d);
        double commission = round2(total * (dv(price.getCommissionPercent()) / 100));
        double subtotal = round2(total + commission);
        double gstprice = round2(subtotal * (dv(price.getGstPercent()) / 100));
        double grandTotal = round2(subtotal + gstprice);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kilometerprice", kilometerprice);
        m.put("minuteprice", minuteprice);
        m.put("tollprice", 0d);
        m.put("basePrice", baseprice);
        m.put("pricesurge", peak);
        m.put("insurance", insurance);
        m.put("others1", o1);
        m.put("others2", o2);
        m.put("others3", o3);
        m.put("peaktimecharge", peak);
        m.put("commission", commission);
        m.put("subtotal", subtotal);
        m.put("gstprice", gstprice);
        m.put("promo", 0d);
        m.put("grandTotal", grandTotal);
        m.put("total", total);
        return m;
    }

    private static double dv(BigDecimal v) {
        return v == null ? 0d : v.doubleValue();
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    // ------------------------------------------------------------------ list endpoints

    private Pageable page(int p, int limit, Sort sort) {
        return PageRequest.of(Math.max(0, p - 1), Math.max(1, limit), sort);
    }

    public Map<String, Object> getOutstationRides(GetOutstationRidesRequest req) {
        boolean hasUser = req.getUserId() != null && !req.getUserId().isBlank();
        boolean hasDriver = req.getDriverId() != null && !req.getDriverId().isBlank();
        if (hasUser == hasDriver) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Exactly one of userId or driverId is required");
        }
        UUID userId = hasUser ? uuid(req.getUserId(), "Invalid userId") : null;
        UUID driverId = hasDriver ? uuid(req.getDriverId(), "Invalid driverId") : null;

        Specification<Ride> spec = (root, q, cb) -> {
            List<jakarta.persistence.criteria.Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("rideType"), "outstation"));
            ps.add(cb.isFalse(root.get("deleted")));
            if (userId != null) {
                ps.add(cb.equal(root.get("userId"), userId));
            }
            if (driverId != null) {
                ps.add(cb.equal(root.get("driverId"), driverId));
                ps.add(cb.notEqual(root.get("status"), "create"));
            }
            if (req.getStatus() != null) {
                ps.add(cb.equal(root.get("status"), req.getStatus()));
            }
            return cb.and(ps.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return pageResponse(spec, req.getPage(), req.getLimit(), false);
    }

    public Map<String, Object> getOutstationRideList(GetOutstationRidesRequest req) {
        UUID userId = uuidOrNull(req.getUserId());
        UUID driverId = uuidOrNull(req.getDriverId());
        if (req.getUserId() != null && userId == null) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Invalid userId");
        }
        if (req.getDriverId() != null && driverId == null) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Invalid driverId");
        }

        Specification<Ride> spec = (root, q, cb) -> {
            List<jakarta.persistence.criteria.Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("rideType"), "outstation"));
            ps.add(cb.isFalse(root.get("deleted")));
            if (userId != null) {
                ps.add(cb.equal(root.get("userId"), userId));
            }
            if (driverId != null) {
                ps.add(cb.equal(root.get("driverId"), driverId));
            }
            if (req.getTripType() != null) {
                ps.add(cb.equal(root.get("outstationTripType"), req.getTripType()));
            }
            if (req.getPayment() != null) {
                ps.add(cb.equal(root.get("paymentMethod"), req.getPayment()));
            }
            if (Boolean.TRUE.equals(req.getOnTripRoute())) {
                ps.add(root.get("status").in(RideConstants.ACTIVE_RIDE_STATUSES));
            } else if (req.getStatus() != null) {
                ps.add(cb.equal(root.get("status"), req.getStatus()));
            } else if (driverId != null) {
                ps.add(cb.notEqual(root.get("status"), "create"));
            }
            if (req.getSearch() != null && !req.getSearch().isBlank()) {
                ps.add(cb.like(cb.lower(root.get("bookingNumber")),
                        "%" + req.getSearch().trim().toLowerCase() + "%"));
            }
            return cb.and(ps.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return pageResponse(spec, req.getPage(), req.getLimit(), true);
    }

    public Map<String, Object> outstationFeeds(OutstationFeedRequest req) {
        JsonNode driverRes = rideAxios.getDriverById(uuid(req.getDriverId(), "Invalid driverId"));
        if (driverRes == null || !driverRes.path("status").asBoolean(false)) {
            throw new ApiException(StatusCode.NOT_FOUND, "Driver not found");
        }
        JsonNode driver = driverRes.path("data");
        UUID driverId = uuid(req.getDriverId(), "Invalid driverId");

        List<String> preferred = new ArrayList<>();
        for (JsonNode d : driver.path("preferredLocations")) {
            preferred.add(d.asText("").toLowerCase());
        }

        String selectedCity = req.getSelectedCity();
        if (selectedCity != null && !preferred.contains(selectedCity.toLowerCase())) {
            throw new ApiException(StatusCode.BAD_REQUEST,
                    "Selected city is not in driver's preferred locations");
        }

        List<String> cities;
        if (selectedCity != null) {
            cities = List.of(selectedCity.toLowerCase());
        } else if (!Boolean.TRUE.equals(req.getSeeAll())) {
            cities = preferred;
        } else {
            cities = null;
        }

        Specification<Ride> spec = (root, q, cb) -> {
            List<jakarta.persistence.criteria.Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("status"), "create"));
            ps.add(cb.isFalse(root.get("deleted")));
            ps.add(cb.isNull(root.get("driverId")));
            ps.add(cb.greaterThanOrEqualTo(root.get("bookingDate"), OffsetDateTime.now()));
            if ("rental".equals(req.getTripType())) {
                ps.add(root.get("rideType").in(List.of("outstation", "rental")));
                ps.add(cb.equal(root.get("outstationTripType"), "rental"));
            } else {
                ps.add(cb.equal(root.get("rideType"), "outstation"));
                if (req.getTripType() != null) {
                    ps.add(cb.equal(root.get("outstationTripType"), req.getTripType()));
                }
            }
            if (cities != null && !cities.isEmpty()) {
                ps.add(cb.or(
                        cb.lower(root.get("locationPickupDistrict")).in(cities),
                        cb.lower(root.get("locationDropoffDistrict")).in(cities)));
            }
            // ignoredDrivers != driverId - native array containment is skipped here (see summary note)
            return cb.and(ps.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        if (Boolean.TRUE.equals(req.getGetCount())) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("totalRides", rideRepository.count(spec));
            return r;
        }
        Map<String, Object> resp = pageResponse(spec, req.getPage(), req.getLimit(), false);
        // drop rides that explicitly ignore this driver
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rides = (List<Map<String, Object>>) resp.get("rides");
        rides.removeIf(m -> {
            Object ig = m.get("ignoredDrivers");
            return ig instanceof List<?> list && list.contains(driverId.toString());
        });
        return resp;
    }

    private Map<String, Object> pageResponse(Specification<Ride> spec, int p, int limit, boolean withProfiles) {
        var pageRes = rideRepository.findAll(spec,
                page(p, limit, Sort.by(Sort.Direction.DESC, "createdAt")));
        long total = pageRes.getTotalElements();

        Map<UUID, VehicleType> typeCache = new LinkedHashMap<>();
        Map<UUID, VehicleVariant> variantCache = new LinkedHashMap<>();
        Map<String, JsonNode> userMap = new LinkedHashMap<>();
        Map<String, JsonNode> driverMap = new LinkedHashMap<>();

        if (withProfiles) {
            List<String> userIds = pageRes.getContent().stream()
                    .map(Ride::getUserId).filter(java.util.Objects::nonNull).map(UUID::toString).distinct().toList();
            List<String> driverIds = pageRes.getContent().stream()
                    .map(Ride::getDriverId).filter(java.util.Objects::nonNull).map(UUID::toString).distinct().toList();
            if (!userIds.isEmpty()) {
                try {
                    JsonNode r = rideAxios.getUsersByIds(userIds);
                    if (r != null && r.path("status").asBoolean(false)) {
                        for (JsonNode u : r.path("data")) {
                            userMap.put(u.path("_id").asText(), u);
                        }
                    }
                } catch (Exception e) {
                    log.warn("getUsersByIds failed: {}", e.getMessage());
                }
            }
            if (!driverIds.isEmpty()) {
                try {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("driverIds", driverIds);
                    JsonNode r = rideAxios.getDriversByIds(body);
                    if (r != null && r.path("status").asBoolean(false)) {
                        for (JsonNode dd : r.path("data")) {
                            driverMap.put(dd.path("_id").asText(), dd);
                        }
                    }
                } catch (Exception e) {
                    log.warn("getDriversByIds failed: {}", e.getMessage());
                }
            }
        }

        List<Map<String, Object>> rides = new ArrayList<>();
        for (Ride ride : pageRes.getContent()) {
            VehicleType vt = null;
            VehicleVariant vv = null;
            if (ride.getVehicleTypeId() != null) {
                vt = typeCache.computeIfAbsent(ride.getVehicleTypeId(),
                        k -> vehicleTypeRepository.findById(k).orElse(null));
            }
            if (ride.getVehicleVariantId() != null) {
                vv = variantCache.computeIfAbsent(ride.getVehicleVariantId(),
                        k -> vehicleVariantRepository.findById(k).orElse(null));
            }
            Map<String, Object> view = toRideView(ride, vt, vv);
            if (withProfiles) {
                view.put("userProfile", ride.getUserId() == null ? null
                        : userMap.get(ride.getUserId().toString()));
                view.put("driverProfile", ride.getDriverId() == null ? null
                        : driverMap.get(ride.getDriverId().toString()));
            }
            rides.add(view);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("totalRides", total);
        resp.put("rides", rides);
        return resp;
    }

    private Map<String, Object> toRideView(Ride ride, VehicleType vt, VehicleVariant vv) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", ride.getId() == null ? null : ride.getId().toString());
        m.put("bookingNumber", ride.getBookingNumber());
        m.put("userId", ride.getUserId() == null ? null : ride.getUserId().toString());
        m.put("driverId", ride.getDriverId() == null ? null : ride.getDriverId().toString());
        m.put("rideType", ride.getRideType());
        m.put("status", ride.getStatus());
        m.put("pickup", latLng(ride.getPickupLat(), ride.getPickupLng()));
        m.put("drop", latLng(ride.getDropLat(), ride.getDropLng()));
        Map<String, Object> loc = new LinkedHashMap<>();
        loc.put("pickup", ride.getLocationPickup());
        loc.put("dropoff", ride.getLocationDropoff());
        loc.put("pickupDistrict", ride.getLocationPickupDistrict());
        loc.put("dropoffDistrict", ride.getLocationDropoffDistrict());
        m.put("location", loc);
        m.put("bookingDate", ride.getBookingDate());
        m.put("endDate", ride.getEndDate());
        m.put("vehicleTypeId", vt != null ? vehicleTypeView(vt)
                : (ride.getVehicleTypeId() == null ? null : ride.getVehicleTypeId().toString()));
        m.put("vehicleVariantId", vv != null ? vehicleVariantView(vv)
                : (ride.getVehicleVariantId() == null ? null : ride.getVehicleVariantId().toString()));
        m.put("totalPrice", ride.getTotalPrice());
        m.put("paymentMethod", ride.getPaymentMethod());
        Map<String, Object> outstation = new LinkedHashMap<>();
        outstation.put("tripType", ride.getOutstationTripType());
        outstation.put("kmEstimated", ride.getOutstationKmEstimated());
        outstation.put("durationEstimated", ride.getOutstationDurationEstimated());
        outstation.put("totalKm", ride.getOutstationTotalKm());
        outstation.put("extraKm", ride.getOutstationExtraKm());
        outstation.put("extraTime", ride.getOutstationExtraTime());
        outstation.put("totalDuration", ride.getOutstationTotalDuration());
        m.put("outstation", outstation);
        List<Map<String, Object>> process = new ArrayList<>();
        for (RideProcess p : ride.getProcess()) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("status", p.getStatus());
            pm.put("createdAt", p.getCreatedAt());
            process.add(pm);
        }
        m.put("process", process);
        m.put("notes", ride.getNotes());
        m.put("extraCarrier", ride.isExtraCarrier());
        m.put("promo", ride.getPromo());
        m.put("requestedDrivers", java.util.Arrays.stream(
                ride.getRequestedDrivers() == null ? new UUID[0] : ride.getRequestedDrivers())
                .map(UUID::toString).toList());
        m.put("ignoredDrivers", java.util.Arrays.stream(
                ride.getIgnoredDrivers() == null ? new UUID[0] : ride.getIgnoredDrivers())
                .map(UUID::toString).toList());
        m.put("createdAt", ride.getCreatedAt());
        m.put("updatedAt", ride.getUpdatedAt());
        return m;
    }

    private static Map<String, Object> latLng(Double lat, Double lng) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lat", lat);
        m.put("lng", lng);
        return m;
    }

    private static Map<String, Object> vehicleTypeView(VehicleType vt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", vt.getId().toString());
        m.put("type", vt.getType());
        m.put("label", vt.getLabel());
        m.put("image", vt.getImage());
        return m;
    }

    private static Map<String, Object> vehicleVariantView(VehicleVariant vv) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", vv.getId().toString());
        m.put("vehicleTypeId", vv.getVehicleTypeId() == null ? null : vv.getVehicleTypeId().toString());
        m.put("type", vv.getType());
        m.put("label", vv.getLabel());
        m.put("image", vv.getImage());
        m.put("isOutstation", vv.isOutstation());
        m.put("isLocal", vv.isLocal());
        return m;
    }

    // ------------------------------------------------------------------ getRentalSlots

    public Map<String, Object> getRentalSlots(RentalSlotsRequest req) {
        UUID variantId = uuid(req.getVehicleVariantId(), "Invalid vehicleVariantId");
        VehicleVariant variant = vehicleVariantRepository.findById(variantId)
                .filter(v -> !v.isDeleted())
                .orElseThrow(() -> new ApiException(StatusCode.NOT_FOUND,
                        "Rental not available for selected vehicle"));
        Price price = resolveOutstationPrice(variant);
        List<PriceRentalPackage> packages = price == null ? List.of()
                : rentalPackageRepository.findByPriceId(price.getId());
        if (packages.isEmpty()) {
            throw new ApiException(StatusCode.NOT_FOUND, "Rental not available for selected vehicle");
        }

        Map<String, Integer> order = Map.of("hour", 1, "day", 2, "week", 3);
        List<Map<String, Object>> slots = packages.stream()
                .sorted((a, b) -> {
                    int diff = order.getOrDefault(a.getDurationType(), 99)
                            - order.getOrDefault(b.getDurationType(), 99);
                    if (diff != 0) {
                        return diff;
                    }
                    return Double.compare(dv(a.getDurationValue()), dv(b.getDurationValue()));
                })
                .map(pkg -> {
                    Map<String, Object> s = new LinkedHashMap<>();
                    s.put("packageId", pkg.getId().toString());
                    s.put("durationType", pkg.getDurationType());
                    s.put("durationValue", pkg.getDurationValue());
                    s.put("kmLimit", pkg.getKmLimit());
                    s.put("baseFare", pkg.getBaseFare());
                    double dvv = dv(pkg.getDurationValue());
                    s.put("label", stripTrailingZeros(dvv) + " " + pkg.getDurationType()
                            + (dvv > 1 ? "s" : "") + " / " + stripTrailingZeros(dv(pkg.getKmLimit())) + " km");
                    return s;
                })
                .toList();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("vehicleVariantId", req.getVehicleVariantId());
        resp.put("vehicleVariantName", variant.getLabel() == null ? "" : variant.getLabel());
        resp.put("slots", slots);
        return resp;
    }

    private static Double dv2(BigDecimal v) {
        return v == null ? 0d : v.doubleValue();
    }

    private static String stripTrailingZeros(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) {
            return String.valueOf((long) d);
        }
        return String.valueOf(d);
    }

    // ------------------------------------------------------------------ get_ride_payment_modes

    public Map<String, Object> getRidePaymentModes(String rideIdStr) {
        UUID rideId = uuid(rideIdStr, "Invalid rideId");
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ApiException(StatusCode.NOT_FOUND, "Ride not found"));

        List<String> paymentModeSetting = new ArrayList<>();
        if (ride.getPriceId() != null) {
            Price price = priceRepository.findById(ride.getPriceId()).orElse(null);
            if (price != null && price.getPaymentModeSetting() != null) {
                paymentModeSetting = new ArrayList<>(java.util.Arrays.asList(price.getPaymentModeSetting()));
            }
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("paymentModeSetting", paymentModeSetting);
        resp.put("allowedModes", normalizePaymentModes(paymentModeSetting));
        return resp;
    }

    /** Port of ride.service.js normalizePaymentModes(settings). */
    private static List<String> normalizePaymentModes(List<String> settings) {
        if (settings == null || settings.isEmpty()) {
            return List.of("cash", "online");
        }
        if (settings.contains("customer_wish")) {
            return List.of("cash", "online");
        }
        List<String> allowed = new ArrayList<>();
        if (settings.contains("cash")) {
            allowed.add("cash");
        }
        if (settings.contains("upi")) {
            allowed.add("online");
        }
        return allowed.isEmpty() ? List.of("cash", "online") : allowed;
    }

    // ------------------------------------------------------------------ getPriceDetailsById

    public Map<String, Object> getPriceDetailsById(String userIdStr) {
        UUID userId = uuid(userIdStr, "Invalid user ID");
        JsonNode userRes = rideAxios.getUserById(userId);

        List<Ride> rides = rideRepository.findAll((root, q, cb) -> cb.and(
                cb.equal(root.get("userId"), userId), cb.isFalse(root.get("deleted"))));

        List<Map<String, Object>> pendingCancel = new ArrayList<>();
        List<Map<String, Object>> dropoffUnpaid = new ArrayList<>();
        List<Map<String, Object>> cancelOnRide = new ArrayList<>();
        List<Map<String, Object>> waitingCharges = new ArrayList<>();

        for (Ride r : rides) {
            List<com.ridehail.rideservice.entity.StarRating> ratings = starRatingRepository.findByRideId(r.getId());
            Double avg = ratings.isEmpty() ? null : ratings.stream()
                    .filter(s -> s.getStarRating() != null)
                    .mapToDouble(s -> s.getStarRating().doubleValue()).average().orElse(0);
            int ratingCount = ratings.size();
            Double rating1 = avg == null ? null : Math.round(avg * 10.0) / 10.0;

            boolean cancelled = "user_cancel".equals(r.getStatus()) || "driver_cancel".equals(r.getStatus());
            JsonNode tp = r.getTotalPrice();

            if (cancelled && !r.isCancelAmountPaid() && r.getCancelAmount().signum() > 0) {
                double amount;
                double limit = tp == null ? 0 : tp.path("usercancellationtimelimit").asDouble(0);
                if (limit > 0) {
                    double charge = tp.path("usercancellationcharges").asDouble(0);
                    amount = Math.round(charge * (r.getCancelMinutes() / limit) * 100.0) / 100.0;
                } else {
                    amount = r.getCancelAmount().doubleValue();
                }
                pendingCancel.add(bucketRow(r, amount, rating1, ratingCount));
            }
            if ("dropoff".equals(r.getStatus()) && !"user".equals(r.getPaidBy())
                    && "cash".equals(r.getPaymentMethod())) {
                double amount = tp == null ? 0 : Math.round(tp.path("grandTotal").asDouble(0) * 100.0) / 100.0;
                dropoffUnpaid.add(bucketRow(r, amount, rating1, ratingCount));
            }
            if (cancelled && r.getCancelAmountOnRide().signum() > 0) {
                double amount = Math.round(r.getCancelAmountOnRide().doubleValue() * 100.0) / 100.0;
                cancelOnRide.add(bucketRow(r, amount, rating1, ratingCount));
            }
            double totalWaiting = r.getWaitingAmount().doubleValue() + r.getTransitWaitingAmount().doubleValue();
            if (totalWaiting > 0) {
                waitingCharges.add(bucketRow(r, totalWaiting, rating1, ratingCount));
            }
        }

        double pendingCancelTotal = sumAmount(pendingCancel);
        double dropoffUnpaidTotal = sumAmount(dropoffUnpaid);
        double cancelOnRideTotal = sumAmount(cancelOnRide);
        double waitingChargesTotal = sumAmount(waitingCharges);

        double totalRatingSum = 0;
        long totalRatingCount = 0;
        for (Map<String, Object> row : concat(pendingCancel, dropoffUnpaid, cancelOnRide, waitingCharges)) {
            Object rc = row.get("ratingCount");
            Object rt = row.get("rating");
            if (rt instanceof Number rtn && rc instanceof Number rcn && rcn.intValue() > 0) {
                totalRatingSum += rtn.doubleValue() * rcn.intValue();
                totalRatingCount += rcn.intValue();
            }
        }
        double overallAvg = totalRatingCount > 0
                ? Math.round((totalRatingSum / totalRatingCount) * 10.0) / 10.0 : 0;

        Map<String, Object> rating = new LinkedHashMap<>();
        rating.put("avgRating", overallAvg);
        rating.put("ratingCount", totalRatingCount);
        rating.put("totalRatingSum", totalRatingSum);

        Map<String, Object> paymentPayload = new LinkedHashMap<>();
        paymentPayload.put("pendingPrice", pendingCancel.stream().map(r -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("_id", r.get("rideId"));
            x.put("cancelprice", r.getOrDefault("amount", 0));
            return x;
        }).toList());
        paymentPayload.put("notGivenPayment", dropoffUnpaid.stream().map(r -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("_id", r.get("rideId"));
            return x;
        }).toList());
        paymentPayload.put("cancelPriceOnRide", cancelOnRide.stream().map(r -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("_id", r.get("rideId"));
            x.put("cancelAmountOnRide", r.getOrDefault("amount", 0));
            return x;
        }).toList());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("pendingCancelTotal", pendingCancelTotal);
        summary.put("dropoffUnpaidTotal", dropoffUnpaidTotal);
        summary.put("cancelOnRideTotal", cancelOnRideTotal);
        summary.put("waitingChargesTotal", waitingChargesTotal);
        summary.put("totalDue", pendingCancelTotal + dropoffUnpaidTotal + cancelOnRideTotal + waitingChargesTotal);

        Map<String, Object> ridesMap = new LinkedHashMap<>();
        ridesMap.put("pendingCancel", pendingCancel);
        ridesMap.put("dropoffUnpaid", dropoffUnpaid);
        ridesMap.put("cancelOnRide", cancelOnRide);
        ridesMap.put("waitingCharges", waitingCharges);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("user", userRes == null ? null : userRes.path("data").path("user"));
        resp.put("rating", rating);
        resp.put("paymentPayload", paymentPayload);
        resp.put("summary", summary);
        resp.put("rides", ridesMap);
        return resp;
    }

    private Map<String, Object> bucketRow(Ride r, double amount, Double rating, int ratingCount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rideId", r.getId().toString());
        m.put("bookingNumber", r.getBookingNumber());
        m.put("status", r.getStatus());
        m.put("amount", Math.round(amount * 100.0) / 100.0);
        m.put("rating", rating);
        m.put("ratingCount", ratingCount);
        m.put("createdAt", r.getCreatedAt());
        return m;
    }

    private static double sumAmount(List<Map<String, Object>> rows) {
        double s = 0;
        for (Map<String, Object> r : rows) {
            Object a = r.get("amount");
            if (a instanceof Number n) {
                s += n.doubleValue();
            }
        }
        return s;
    }

    @SafeVarargs
    private static List<Map<String, Object>> concat(List<Map<String, Object>>... lists) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (List<Map<String, Object>> l : lists) {
            out.addAll(l);
        }
        return out;
    }
}
