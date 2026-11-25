package com.ridehail.rideservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.common.realtime.SocketRelayPublisher;
import com.ridehail.common.security.AuthContext;
import com.ridehail.rideservice.client.RideAxios;
import com.ridehail.rideservice.constants.RideConstants;
import com.ridehail.rideservice.dto.AddGpsPointRequest;
import com.ridehail.rideservice.dto.CancelRideByUserRequest;
import com.ridehail.rideservice.dto.CreateRideRequest;
import com.ridehail.rideservice.dto.UpdateRideRequest;
import com.ridehail.rideservice.entity.Price;
import com.ridehail.rideservice.entity.Ride;
import com.ridehail.rideservice.entity.RideGps;
import com.ridehail.rideservice.entity.RideProcess;
import com.ridehail.rideservice.entity.StarRating;
import com.ridehail.rideservice.entity.VehicleVariant;
import com.ridehail.rideservice.realtime.RideDriverUpdatePublisher;
import com.ridehail.rideservice.realtime.RideNotificationPublisher;
import com.ridehail.rideservice.repository.PriceRepository;
import com.ridehail.rideservice.repository.RideAdminPaymentRepository;
import com.ridehail.rideservice.repository.RideDriverPaymentRepository;
import com.ridehail.rideservice.repository.RideGpsRepository;
import com.ridehail.rideservice.repository.RidePaymentRepository;
import com.ridehail.rideservice.repository.RideRepository;
import com.ridehail.rideservice.repository.StarRatingRepository;
import com.ridehail.rideservice.repository.VehicleVariantRepository;
import com.ridehail.rideservice.entity.RidePayment;

/**
 * Bucket "ride-lifecycle-and-realtime" of ride-service. Ports these ride.controller.js functions:
 * create_ride, update_ride, get_ride, get_all_ride, getRideDataById, acceptRide, addGPSPoint,
 * getGPSPoint, pickupRide, startRide, dropoffRide, ride_completePayment, endRide, updateVehicleType,
 * cancelRideByUser, fetchRide, updateIgnoredDriver, rideCancelByDriver, suddenendByUser.
 *
 * The functions with no REST route (pickupRide/startRide/dropoffRide-as-pubsub/ride_completePayment/
 * endRide/updateVehicleType/fetchRide/updateIgnoredDriver/rideCancelByDriver/suddenendByUser and
 * the pubsub form of acceptRide/addGPSPoint/cancelRideByUser) are invoked only from
 * {@link com.ridehail.rideservice.realtime.RideLifecyclePubSubConfig}, mirroring helpers/ride.subscriber.js.
 */
@Service
public class RideLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(RideLifecycleService.class);

    private final ObjectMapper mapper = new ObjectMapper();

    private final RideRepository rideRepository;
    private final RideGpsRepository rideGpsRepository;
    private final VehicleVariantRepository vehicleVariantRepository;
    private final PriceRepository priceRepository;
    private final StarRatingRepository starRatingRepository;
    private final RidePaymentRepository ridePaymentRepository;
    private final RideAdminPaymentRepository rideAdminPaymentRepository;
    private final RideDriverPaymentRepository rideDriverPaymentRepository;
    private final RideAxios axios;
    private final SocketRelayPublisher socket;
    private final RideNotificationPublisher notifications;
    private final RideDriverUpdatePublisher driverUpdates;

    public RideLifecycleService(RideRepository rideRepository, RideGpsRepository rideGpsRepository,
            VehicleVariantRepository vehicleVariantRepository, PriceRepository priceRepository,
            StarRatingRepository starRatingRepository, RidePaymentRepository ridePaymentRepository,
            RideAdminPaymentRepository rideAdminPaymentRepository,
            RideDriverPaymentRepository rideDriverPaymentRepository, RideAxios axios,
            SocketRelayPublisher socket, RideNotificationPublisher notifications,
            RideDriverUpdatePublisher driverUpdates) {
        this.rideRepository = rideRepository;
        this.rideGpsRepository = rideGpsRepository;
        this.vehicleVariantRepository = vehicleVariantRepository;
        this.priceRepository = priceRepository;
        this.starRatingRepository = starRatingRepository;
        this.ridePaymentRepository = ridePaymentRepository;
        this.rideAdminPaymentRepository = rideAdminPaymentRepository;
        this.rideDriverPaymentRepository = rideDriverPaymentRepository;
        this.axios = axios;
        this.socket = socket;
        this.notifications = notifications;
        this.driverUpdates = driverUpdates;
    }

    // ================================================================= helpers

    private static UUID uuid(String s) {
        try {
            return s == null || s.isBlank() ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, "Invalid id");
        }
    }

    private static UUID uuidOrNull(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) {
            return null;
        }
        try {
            return UUID.fromString(n.asText());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static double d(BigDecimal v) {
        return v == null ? 0d : v.doubleValue();
    }

    /** helpers/ride.commonFunction.js parseWithFix(str, 2). */
    private static BigDecimal fix2(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    /** helpers/ride.commonFunction.js genotp() -> 1000..9999. */
    private static String genotp() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(1000, 10000));
    }

    /** helpers/ride.commonFunction.js sectomin(seconds) = Math.floor(seconds/60). */
    private static int sectomin(Integer seconds) {
        return seconds == null ? 0 : Math.floorDiv(seconds, 60);
    }

    /** helpers/ride.commonFunction.js generateBookingNumber(). */
    private String generateBookingNumber() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String timestampStr = timestamp.length() > 6 ? timestamp.substring(3, timestamp.length() - 3) : timestamp;
        long count = rideRepository.count();
        return "KR" + timestampStr + count;
    }

    private Ride requireRide(UUID id) {
        return rideRepository.findById(id).orElseThrow(() -> new ApiException(404, "Ride not found"));
    }

    private void pushProcess(Ride ride, String status) {
        ride.getProcess().add(RideProcess.builder().ride(ride).status(status).build());
    }

    private void applyPickupDrop(Ride ride, Double pickupLat, Double pickupLng, Double dropLat, Double dropLng) {
        ride.setPickupLat(pickupLat);
        ride.setPickupLng(pickupLng);
        ride.setDropLat(dropLat);
        ride.setDropLng(dropLng);
        ride.setCoordinatesPickupLat(pickupLat);
        ride.setCoordinatesPickupLng(pickupLng);
        ride.setCoordinatesDropoffLat(dropLat);
        ride.setCoordinatesDropoffLng(dropLng);
    }

    private String currentUserId() {
        AuthContext ctx = AuthContext.current();
        if (ctx == null || ctx.principalId() == null) {
            throw new ApiException(400, "Invalid userId");
        }
        return ctx.principalId();
    }

    private JsonNode currentPrincipal() {
        AuthContext ctx = AuthContext.current();
        return ctx == null ? null : ctx.principal();
    }

    private void socketToMultipleUsers(List<String> ids, String event, Object data) {
        if (ids == null) {
            return;
        }
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                socket.toUser(id, event, data);
            }
        }
    }

    /** config/socketemitter.js toNearbyDrivers legacy bug: emits to room user:<id>, not driver:<id>. Preserved. */
    private void socketToNearbyDrivers(List<String> driverIds, String event, Object data) {
        socketToMultipleUsers(driverIds, event, data);
    }

    private static List<UUID> toUuidList(JsonNode arrayNode) {
        List<UUID> out = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode n : arrayNode) {
                UUID u = uuidOrNull(n.path("_id").isMissingNode() ? n : n.path("_id"));
                if (u != null) {
                    out.add(u);
                }
            }
        }
        return out;
    }

    // ================================================================= create_ride

    @Transactional
    public Map<String, Object> createRide(CreateRideRequest req) {
        String userId = currentUserId();
        UUID vehicleTypeId = uuid(req.getVehicleTypeId());
        boolean isBookAny = Boolean.TRUE.equals(req.getIsBookAny());
        UUID vehicleVariantId = isBookAny ? null : uuid(req.getVehicleVariantId());
        if (!isBookAny && vehicleVariantId == null) {
            throw new ApiException(400, "Invalid vehicleVariantId");
        }

        String bookingNumber = generateBookingNumber();

        Ride ride = new Ride();
        ride.setUserId(uuid(userId));
        ride.setRideType(req.getRideType() == null ? "local" : req.getRideType());
        ride.setBookingNumber(bookingNumber);
        ride.setLocationPickup(nz(req.getPickupAddress()));
        ride.setLocationDropoff(nz(req.getDropAddress()));
        ride.setLocationPickupDistrict(nz(req.getPickupDistrict()));
        ride.setLocationDropoffDistrict(nz(req.getDropoffDistrict()));
        ride.setStatus("create");
        ride.setVehicleTypeId(vehicleTypeId);
        if (!isBookAny) {
            ride.setVehicleVariantId(vehicleVariantId);
        }
        if (req.getKm() != null) {
            ride.setKm(BigDecimal.valueOf(req.getKm()));
        }
        if (req.getTime() != null) {
            ride.setRideTimeSeconds(req.getTime());
        }
        applyPickupDrop(ride, req.getPickup().getLat(), req.getPickup().getLng(),
                req.getDrop() == null ? null : req.getDrop().getLat(),
                req.getDrop() == null ? null : req.getDrop().getLng());
        pushProcess(ride, "create");

        // promo
        if (req.getPromo() != null && !req.getPromo().isBlank()) {
            JsonNode coupon = axios.getCouponById(req.getPromo());
            JsonNode data = coupon.path("data");
            if (coupon.path("status").asBoolean(false) && data.isObject() && data.size() > 0) {
                ObjectNode couponData = mapper.createObjectNode();
                couponData.set("_id", data.path("_id"));
                couponData.set("code", data.path("code"));
                couponData.set("discountType", data.path("discountType"));
                couponData.set("discountValue", data.path("discountValue"));
                couponData.set("tripType", data.path("tripType"));
                ride.setPromo(couponData);
            } else {
                throw new ApiException(400, "Invalid coupon code");
            }
        }

        // nearby drivers
        Map<String, Object> reqPayload = new LinkedHashMap<>();
        reqPayload.put("pickup", Map.of("lat", req.getPickup().getLat(), "lng", req.getPickup().getLng()));
        reqPayload.put("vehicleTypeId", req.getVehicleTypeId());
        if (!isBookAny) {
            reqPayload.put("vehicleVariantId", req.getVehicleVariantId());
        }
        reqPayload.put("radiusMeters", 5000);
        reqPayload.put("limit", 10);
        reqPayload.put("onlyOnline", true);

        JsonNode nearby = axios.getNearByDrivers(reqPayload);
        JsonNode nearbyData = nearby.path("data");
        if (!nearby.path("status").asBoolean(false) || !nearbyData.isArray() || nearbyData.isEmpty()) {
            throw new ApiException(400, "No drivers found near your pickup location");
        }

        // cancel any prior create-state local ride for this user
        for (Ride prior : rideRepository.findByUserIdAndStatusAndRideType(uuid(userId), "create", "local")) {
            prior.setStatus("cancelled");
            pushProcess(prior, "cancelled");
            rideRepository.save(prior);
        }

        List<UUID> candidateIds = toUuidList(nearbyData);
        ride.setRequestedDrivers(candidateIds.toArray(UUID[]::new));

        Ride saved = rideRepository.save(ride);

        // socket + notification (fire and forget)
        JsonNode principal = currentPrincipal();
        Map<String, Object> socketPayload = new LinkedHashMap<>();
        socketPayload.put("rideId", saved.getId());
        socketPayload.put("bookingNumber", bookingNumber);
        socketPayload.put("pickup", Map.of("lat", req.getPickup().getLat(), "lng", req.getPickup().getLng()));
        if (req.getDrop() != null) {
            socketPayload.put("drop", Map.of("lat", req.getDrop().getLat(), "lng", req.getDrop().getLng()));
        }
        socketPayload.put("vehicleTypeId", req.getVehicleTypeId());
        socketPayload.put("vehicleVariantId", req.getVehicleVariantId());

        List<String> candidateStrIds = candidateIds.stream().map(UUID::toString).toList();
        socketToNearbyDrivers(candidateStrIds, "ride_requested", socketPayload);

        Map<String, Object> notif = new LinkedHashMap<>();
        notif.put("status", "create");
        notif.put("androidData", Map.of("id", saved.getId(), "bookingNumber", bookingNumber));
        if (principal != null) {
            notif.put("user", principal.path("fcm").asText(null));
        }
        notif.put("driverIds", candidateStrIds);
        notif.put("userId", userId);
        notifications.sendNotification(notif);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("rideId", saved.getId());
        resp.put("bookingNumber", bookingNumber);
        resp.put("driverCount", candidateIds.size());
        resp.put("ride", saved);
        return resp;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    // ================================================================= update_ride

    @Transactional
    public Map<String, Object> updateRide(UpdateRideRequest req) {
        String userId = currentUserId();
        UUID rideId = uuid(req.get_id());
        boolean isBookAny = Boolean.TRUE.equals(req.getIsBookAny());
        UUID vehicleTypeId = uuid(req.getVehicleTypeId());
        UUID vehicleVariantId = isBookAny ? null : uuid(req.getVehicleVariantId());
        if (!isBookAny && vehicleVariantId == null) {
            throw new ApiException(400, "Invalid vehicleVariantId");
        }

        Ride ride = rideRepository.findById(rideId).orElseThrow(() -> new ApiException(400, "Ride not found"));

        ride.setUserId(uuid(userId));
        ride.setRideType(req.getRideType() == null ? "local" : req.getRideType());
        ride.setLocationPickup(nz(req.getPickupAddress()));
        ride.setLocationDropoff(nz(req.getDropAddress()));
        ride.setLocationPickupDistrict(nz(req.getPickupDistrict()));
        ride.setLocationDropoffDistrict(nz(req.getDropoffDistrict()));
        ride.setStatus("updated");
        ride.setVehicleTypeId(vehicleTypeId);
        if (!isBookAny) {
            ride.setVehicleVariantId(vehicleVariantId);
        }
        if (req.getKm() != null) {
            ride.setKm(BigDecimal.valueOf(req.getKm()));
        }
        if (req.getTime() != null) {
            ride.setRideTimeSeconds(req.getTime());
        }
        applyPickupDrop(ride, req.getPickup().getLat(), req.getPickup().getLng(),
                req.getDrop() == null ? null : req.getDrop().getLat(),
                req.getDrop() == null ? null : req.getDrop().getLng());

        if (req.getPromo() != null && !req.getPromo().isBlank()) {
            JsonNode coupon = axios.getCouponById(req.getPromo());
            JsonNode data = coupon.path("data");
            if ("true".equals(coupon.path("status").asText()) && data.isObject() && data.size() > 0) {
                ObjectNode couponData = mapper.createObjectNode();
                couponData.set("_id", data.path("_id"));
                couponData.set("code", data.path("code"));
                couponData.set("discountType", data.path("discountType"));
                couponData.set("discountValue", data.path("discountValue"));
                couponData.set("tripType", data.path("tripType"));
                ride.setPromo(couponData);
            } else {
                throw new ApiException(400, "Invalid coupon code");
            }
        }

        Map<String, Object> reqPayload = new LinkedHashMap<>();
        reqPayload.put("pickup", Map.of("lat", req.getPickup().getLat(), "lng", req.getPickup().getLng()));
        reqPayload.put("vehicleTypeId", req.getVehicleTypeId());
        if (!isBookAny) {
            reqPayload.put("vehicleVariantId", req.getVehicleVariantId());
        }
        reqPayload.put("radiusMeters", 5000);
        reqPayload.put("limit", 10);
        reqPayload.put("onlyOnline", true);

        JsonNode nearby = axios.getNearByDrivers(reqPayload);
        JsonNode nearbyData = nearby.path("data");
        if (!nearby.path("status").asBoolean(false) || !nearbyData.isArray() || nearbyData.isEmpty()) {
            throw new ApiException(400, "No drivers found near your pickup location");
        }

        List<UUID> candidateIds = toUuidList(nearbyData);
        List<UUID> merged = new ArrayList<>();
        if (ride.getRequestedDrivers() != null) {
            for (UUID u : ride.getRequestedDrivers()) {
                merged.add(u);
            }
        }
        merged.addAll(candidateIds);
        ride.setRequestedDrivers(merged.toArray(UUID[]::new));
        pushProcess(ride, "updated");

        Ride saved = rideRepository.save(ride);

        List<String> candidateStrIds = candidateIds.stream().map(UUID::toString).toList();
        Map<String, Object> socketPayload = new LinkedHashMap<>();
        socketPayload.put("rideId", saved.getId());
        socketPayload.put("bookingNumber", saved.getBookingNumber());
        socketToNearbyDrivers(candidateStrIds, "ride_requested", socketPayload);

        Map<String, Object> notif = new LinkedHashMap<>();
        notif.put("status", "create");
        notif.put("androidData", Map.of("id", saved.getId(), "bookingNumber", saved.getBookingNumber()));
        notif.put("userId", userId);
        notif.put("driverIds", candidateStrIds);
        notifications.sendNotification(notif);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("rideId", saved.getId());
        resp.put("bookingNumber", saved.getBookingNumber());
        resp.put("driverCount", candidateIds.size());
        resp.put("ride", saved);
        return resp;
    }

    // ================================================================= get_ride

    @Transactional(readOnly = true)
    public Map<String, Object> getRide(String rideIdStr) {
        UUID rideId = uuid(rideIdStr);
        Ride ride = rideRepository.findById(rideId).orElseThrow(() -> new ApiException(404, "Ride not found"));

        JsonNode userProfile = null;
        JsonNode driverProfile = null;

        JsonNode userRes = axios.getUserById(ride.getUserId());
        if (userRes != null) {
            userProfile = userRes.has("data") ? userRes.get("data") : userRes;
        }
        if (ride.getDriverId() != null) {
            JsonNode driverRes = axios.getDriverById(ride.getDriverId());
            if (driverRes != null) {
                driverProfile = driverRes.has("data") ? driverRes.get("data") : driverRes;
            }
        }

        Optional<StarRating> userRating = starRatingRepository.findByRideIdAndFromTypeAndDeletedFalse(rideId, "user");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ride", ride);
        payload.put("driverProfile", driverProfile);
        payload.put("userProfile", userProfile);
        if (userRating.isPresent()) {
            Map<String, Object> ur = new LinkedHashMap<>();
            ur.put("starrating", userRating.get().getStarRating());
            ur.put("reason", userRating.get().getReason());
            payload.put("userRating", ur);
        } else {
            payload.put("userRating", null);
        }
        return payload;
    }

    // ================================================================= get_all_ride

    @Transactional(readOnly = true)
    public Map<String, Object> getAllRide(Integer pageParam, Integer limitParam, String fromDate, String toDate,
            String rideType, String search) {
        int page = Math.max(pageParam == null ? 1 : pageParam, 1);
        int limit = Math.max(limitParam == null ? 10 : limitParam, 1);

        Specification<Ride> spec = (root, q, cb) -> cb.isFalse(root.get("deleted"));
        if (rideType != null && !rideType.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("rideType"), rideType));
        }
        if (search != null && !search.trim().isEmpty()) {
            String like = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("bookingNumber")), like),
                    cb.like(cb.lower(root.get("paymentMethod")), like)));
        }
        if (fromDate != null && !fromDate.isBlank()) {
            OffsetDateTime from = OffsetDateTime.parse(fromDate);
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (toDate != null && !toDate.isBlank()) {
            OffsetDateTime to = OffsetDateTime.parse(toDate);
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }

        Pageable pageable = limitParam != null
                ? PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt"))
                : PageRequest.of(0, Integer.MAX_VALUE, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Ride> rides = rideRepository.findAll(spec, pageable);

        List<String> driverIds = rides.stream().map(Ride::getDriverId).filter(java.util.Objects::nonNull)
                .map(UUID::toString).distinct().toList();
        List<String> userIds = rides.stream().map(Ride::getUserId).filter(java.util.Objects::nonNull)
                .map(UUID::toString).distinct().toList();

        JsonNode users = axios.getUsersByIds(Map.of("userIds", userIds));
        JsonNode drivers = axios.getDriversByIds(Map.of("driverIds", driverIds));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rides", rides.getContent());
        out.put("total", rides.getTotalElements());
        out.put("page", page);
        out.put("limit", limit);
        out.put("users", users == null ? null : (users.has("data") ? users.get("data") : users));
        out.put("drivers", drivers == null ? null : (drivers.has("data") ? drivers.get("data") : drivers));
        return out;
    }

    // ================================================================= getRideDataById

    /** Port of ride.controller.js getRideDataById(id, userType) - ride enriched with user/driver/admin records. */
    @Transactional(readOnly = true)
    public Map<String, Object> getRideDataById(UUID id, String userType) {
        Ride ride = rideRepository.findById(id).orElse(null);
        if (ride == null) {
            return null;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ride", ride);

        JsonNode userData = !"user".equals(userType) && ride.getUserId() != null
                ? axios.getUserById(ride.getUserId()) : null;
        JsonNode driverData = !"driver".equals(userType) && ride.getDriverId() != null
                ? axios.getDriverById(ride.getDriverId()) : null;
        JsonNode adminData = axios.getAdmin();

        if (driverData != null && driverData.has("data")) {
            data.put("driverId", driverData.get("data"));
        }
        if (userData != null && userData.has("data")) {
            JsonNode dd = userData.get("data");
            data.put("userId", dd.has("user") ? dd.get("user") : dd);
        }
        if (adminData != null && adminData.has("data")) {
            data.put("adminId", adminData.get("data"));
        }
        data.put("otp", ride.getOtp());
        data.put("status", ride.getStatus());
        return data;
    }

    // ================================================================= acceptRide (pubsub)

    @Transactional
    public void acceptRide(JsonNode payload) {
        try {
            UUID id = uuidOrNull(payload.path("_id"));
            if (id == null) {
                return;
            }
            JsonNode driver = payload.path("driver");
            String vehicleVariant = driver.path("vehicleVariant").asText(null);
            UUID driverId = uuidOrNull(driver.path("_id"));
            if (vehicleVariant == null || vehicleVariant.isBlank()) {
                if (driverId != null) {
                    socket.toUser(driverId.toString(), RideConstants.SK_RIDE_ACCEPT_RESPONSE,
                            Map.of("action", RideConstants.RR_RIDE_ACCEPT_ERR,
                                    "msg", "Vehicle varient id is missing on user auth"));
                }
                return;
            }

            Ride ride = rideRepository.findById(id).orElse(null);
            if (ride == null || !List.of("create", "updated", "assigned").contains(ride.getStatus())) {
                if (driverId != null) {
                    socket.toUser(driverId.toString(), RideConstants.SK_RIDE_ACCEPT_RESPONSE,
                            Map.of("action", RideConstants.RR_RIDE_ACCEPT_ERR, "msg", "Ride already accepted",
                                    "status", false));
                }
                return;
            }

            if (!"outstation".equals(ride.getRideType())) {
                VehicleVariant variant = vehicleVariantRepository.findById(uuid(vehicleVariant)).orElse(null);
                Price price = variant != null && variant.getLocalPriceId() != null
                        ? priceRepository.findById(variant.getLocalPriceId()).orElse(null) : null;
                if (price == null) {
                    if (driverId != null) {
                        socket.toUser(driverId.toString(), RideConstants.SK_RIDE_ACCEPT_RESPONSE,
                                Map.of("action", RideConstants.RR_RIDE_ACCEPT_ERR, "msg", "Price not found",
                                        "status", false));
                    }
                    return;
                }
                ObjectNode totalPrice = computeLocalAcceptFare(ride, price);
                ride.setTotalPrice(totalPrice);
                ride.setVehicleVariantId(uuid(vehicleVariant));
            }

            ride.setDriverId(driverId);
            ride.setStatus("accept");
            ride.setOtp(genotp());
            pushProcess(ride, "accept");
            Ride saved = rideRepository.save(ride);

            // coupon usage
            if (saved.getPromo() != null && saved.getPromo().isObject() && saved.getPromo().size() > 0) {
                Map<String, Object> couponPayload = new LinkedHashMap<>();
                couponPayload.put("userId", saved.getUserId());
                couponPayload.put("rideId", saved.getId());
                couponPayload.put("couponId", saved.getPromo().path("_id").asText(null));
                couponPayload.put("discountAmount", saved.getTotalPrice() == null ? 0
                        : saved.getTotalPrice().path("promo").asDouble(0));
                axios.createCouponUsage(couponPayload);
            }

            Map<String, Object> rideData = getRideDataById(id, null);

            List<String> alertDrivers = new ArrayList<>();
            if (saved.getRequestedDrivers() != null) {
                for (UUID rd : saved.getRequestedDrivers()) {
                    if (!rd.equals(driverId)) {
                        alertDrivers.add(rd.toString());
                    }
                }
            }
            List<String> userDriver = new ArrayList<>();
            if (driverId != null) {
                userDriver.add(driverId.toString());
            }
            if (saved.getUserId() != null) {
                userDriver.add(saved.getUserId().toString());
            }

            socketToMultipleUsers(alertDrivers, RideConstants.SK_RIDE_ACCEPT_RESPONSE,
                    Map.of("action", RideConstants.RR_ANOTHER_ACCEPT, "msg",
                            "Ride accepted by another driver", "status", false));
            socketToMultipleUsers(userDriver, RideConstants.SK_RIDE_ACCEPT_RESPONSE,
                    Map.of("action", RideConstants.RR_RIDE_ACCEPT, "msg",
                            "Your ride accepted successfully", "data", rideData, "status", true));

            driverUpdates.onUpdateDriver(Map.of("driverId", driverId, "tripId", id,
                    "status", RideConstants.DRIVER_STATUS_RIDE_ACCEPT));

            Map<String, Object> notif = new LinkedHashMap<>();
            notif.put("status", "accept");
            notif.put("androidData", Map.of("id", id));
            notif.put("userId", saved.getUserId());
            notif.put("driverId", driverId);
            notifications.sendNotification(notif);
        } catch (Exception e) {
            log.error("Error on acceptRide----> {}", e.getMessage());
            UUID driverId = uuidOrNull(payload.path("driver").path("_id"));
            if (driverId != null) {
                socket.toUser(driverId.toString(), RideConstants.SK_RIDE_ACCEPT_RESPONSE,
                        Map.of("action", RideConstants.RR_RIDE_ACCEPT_ERR, "msg", "Your ride not accepted",
                                "status", false));
            }
        }
    }

    /**
     * Port of the local-ride fare block inside ride.controller.js acceptRide (kilometer/minute/toll
     * + surcharges - promo - referral discount, then commission + GST). Mirrors the
     * {@code totalPrice} object it stores.
     */
    private ObjectNode computeLocalAcceptFare(Ride ride, Price price) {
        double km = d(ride.getKm());
        double istoll = ride.isToll() ? 1 : 0;
        int time = sectomin(ride.getRideTimeSeconds());

        double kilometerPrice = fix2(km * d(price.getPricePerKm())).doubleValue();
        double minutePrice = fix2(time * d(price.getPricePerMin())).doubleValue();
        double tollPrice = fix2(istoll * d(price.getTollTax())).doubleValue();
        double total = kilometerPrice + minutePrice + tollPrice + d(price.getPriceSurge())
                + d(price.getInsurance()) + d(price.getOthers1()) + d(price.getOthers2())
                + d(price.getOthers3()) + d(price.getBasePrice()) + d(price.getPeakTimeCharge());

        double discountAmount = 0;
        JsonNode promo = ride.getPromo();
        if (promo != null && promo.isObject() && promo.size() > 0) {
            double dv = promo.path("discountValue").asDouble(0);
            if ("percentage".equals(promo.path("discountType").asText())) {
                discountAmount = fix2(total * dv / 100).doubleValue();
            } else {
                discountAmount = fix2(dv).doubleValue();
            }
            discountAmount = Math.min(discountAmount, total);
        }
        total -= discountAmount;

        double referralDiscount = 0;
        double referralDiscountPercent = 0;
        JsonNode refResp = axios.consumeReferralDiscount(Map.of("userId", ride.getUserId(), "rideId", ride.getId()));
        if (refResp != null && refResp.path("success").asBoolean(false) && refResp.path("data").path("applied").asBoolean(false)) {
            referralDiscountPercent = refResp.path("data").path("discountPercent").asDouble(0);
            referralDiscount = Math.min(fix2(total * referralDiscountPercent / 100).doubleValue(), total);
            total = fix2(total - referralDiscount).doubleValue();
        }

        double commissionValue = fix2(total * d(price.getCommissionPercent()) / 100).doubleValue();
        double subTotal = fix2(total + commissionValue).doubleValue();
        double gstPrice = fix2(subTotal * d(price.getGstPercent()) / 100).doubleValue();
        double grandTotal = fix2(subTotal + gstPrice).doubleValue();

        ObjectNode tp = mapper.createObjectNode();
        tp.put("kilometerPrice", kilometerPrice);
        tp.put("minutePrice", minutePrice);
        tp.put("tollPrice", tollPrice);
        tp.put("pricePerKm", d(price.getPricePerKm()));
        tp.put("pricePerMin", d(price.getPricePerMin()));
        tp.put("priceSurge", d(price.getPriceSurge()));
        tp.put("insurance", d(price.getInsurance()));
        tp.put("tollTax", d(price.getTollTax()));
        tp.put("gstPercent", d(price.getGstPercent()));
        tp.put("gstPrice", gstPrice);
        tp.put("commissionPercent", d(price.getCommissionPercent()));
        tp.put("peaktimecharge", d(price.getPeakTimeCharge()));
        tp.put("usercancellationtimelimit", price.getUserCancellationTimeLimitMinutes());
        tp.put("usercancellationcharges", d(price.getUserCancellationCharge()));
        tp.put("waitingtimelimit", price.getWaitingTimeLimitMinutes());
        tp.put("waitingchargespermin", d(price.getWaitingChargePerMin()));
        tp.put("transitwaitingfeepermin", d(price.getTransitWaitingFeePerMin()));
        tp.put("others1", d(price.getOthers1()));
        tp.put("others2", d(price.getOthers2()));
        tp.put("others3", d(price.getOthers3()));
        tp.put("basePrice", d(price.getBasePrice()));
        tp.put("total", total);
        tp.put("subtotal", subTotal);
        tp.put("commission", commissionValue);
        tp.put("grandTotal", grandTotal);
        tp.put("promo", discountAmount);
        tp.put("referralDiscount", referralDiscount);
        tp.put("referralDiscountPercent", referralDiscountPercent);
        return tp;
    }

    // ================================================================= addGPSPoint

    @Transactional
    public Map<String, Object> addGPSPoint(AddGpsPointRequest req) {
        if (req.getCoordinates() == null || req.getCoordinates().size() != 2) {
            throw new ApiException(404, "Missing required fields");
        }
        double longitude = req.getCoordinates().get(0);
        double latitude = req.getCoordinates().get(1);
        return addGpsCore(req.getRideId(), req.getDriverId(), latitude, longitude,
                req.getSpeed(), req.getHeading(), req.getAccuracy());
    }

    @Transactional
    public void addGPSPoint(JsonNode payload) {
        JsonNode coords = payload.path("coordinates");
        if (!coords.isArray() || coords.size() != 2) {
            return;
        }
        addGpsCore(payload.path("rideId").asText(null), payload.path("driverId").asText(null),
                coords.get(1).asDouble(), coords.get(0).asDouble(),
                payload.hasNonNull("speed") ? payload.get("speed").asDouble() : null,
                payload.hasNonNull("heading") ? payload.get("heading").asDouble() : null,
                payload.hasNonNull("accuracy") ? payload.get("accuracy").asDouble() : null);
    }

    private Map<String, Object> addGpsCore(String rideIdStr, String driverIdStr, double latitude,
            double longitude, Double speed, Double heading, Double accuracy) {
        if (latitude == 0 || longitude == 0) {
            throw new ApiException(400, "Invalid coordinates");
        }
        UUID rideId = uuid(rideIdStr);
        UUID driverId = uuid(driverIdStr);

        Ride ride = rideRepository.findById(rideId).orElseThrow(() -> new ApiException(404, "Ride not found"));

        RideGps gps = RideGps.builder()
                .rideId(rideId)
                .driverId(driverId)
                .userId(ride.getUserId())
                .latitude(latitude)
                .longitude(longitude)
                .speed(speed)
                .heading(heading)
                .accuracy(accuracy)
                .recordedAt(OffsetDateTime.now())
                .build();
        RideGps saved = rideGpsRepository.save(gps);

        axios.updateDriverLocation(Map.of("driverId", driverIdStr, "lat", latitude, "lng", longitude));

        if (ride.getUserId() != null) {
            Map<String, Object> gpsSocketPayload = new LinkedHashMap<>();
            gpsSocketPayload.put("rideId", rideIdStr);
            gpsSocketPayload.put("driverId", driverIdStr);
            gpsSocketPayload.put("userId", ride.getUserId());
            gpsSocketPayload.put("coordinates", Map.of("latitude", latitude, "longitude", longitude));
            socket.toUser(ride.getUserId().toString(), RideConstants.SK_RIDE_GPS_POINT_UPDATE, gpsSocketPayload);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("gpsPoint", saved);
        return out;
    }

    @Transactional(readOnly = true)
    public List<RideGps> getGPSPoint(String rideIdStr) {
        return rideGpsRepository.findByRideIdOrderByRecordedAtAsc(uuid(rideIdStr));
    }

    // ================================================================= pickupRide / startRide (pubsub)

    @Transactional
    public void pickupRide(JsonNode payload) {
        try {
            UUID id = uuidOrNull(payload.path("_id"));
            String driverId = payload.path("driverId").asText(null);
            Ride ride = rideRepository.findById(id).orElseThrow();
            ride.setStatus("pickup");
            pushProcess(ride, "pickup");
            Ride saved = rideRepository.save(ride);

            Map<String, Object> rideData = getRideDataById(id, null);
            List<String> whom = new ArrayList<>();
            if (saved.getUserId() != null) {
                whom.add(saved.getUserId().toString());
            }
            if (saved.getDriverId() != null) {
                whom.add(saved.getDriverId().toString());
            }
            socketToMultipleUsers(whom, RideConstants.SK_RIDE_PICKUP_RESPONSE, rideData);
            notifications.sendNotification(Map.of("status", "pickup",
                    "androidData", Map.of("otp", nz(saved.getOtp()))));
            driverUpdates.onUpdateDriver(Map.of("driverId", driverId, "tripId", id,
                    "status", RideConstants.DRIVER_STATUS_RIDE_PICKUP));
        } catch (Exception e) {
            log.error("Error on pickupRide----> {}", e.getMessage());
        }
    }

    @Transactional
    public void startRide(JsonNode payload) {
        try {
            UUID id = uuidOrNull(payload.path("_id"));
            Ride ride = rideRepository.findById(id).orElseThrow();

            int waitingMinute = 0;
            Optional<RideProcess> pickupProcess = ride.getProcess().stream()
                    .filter(p -> "pickup".equals(p.getStatus())).findFirst();
            if (pickupProcess.isPresent() && ride.getVehicleVariantId() != null) {
                VehicleVariant variant = vehicleVariantRepository.findById(ride.getVehicleVariantId()).orElse(null);
                UUID priceId = variant == null ? null
                        : "outstation".equals(ride.getRideType()) ? variant.getOutstationPriceId()
                        : variant.getLocalPriceId();
                Price price = priceId == null ? null : priceRepository.findById(priceId).orElse(null);
                long waited = java.time.Duration.between(pickupProcess.get().getUpdatedAt(), OffsetDateTime.now()).toMinutes();
                int limit = price == null ? 0 : nz0(price.getWaitingTimeLimitMinutes());
                waitingMinute = (int) Math.max(0, waited - limit);
            }

            ride.setStatus("startride");
            ride.setWaitingMinute(waitingMinute);
            pushProcess(ride, "startride");
            Ride saved = rideRepository.save(ride);

            List<String> whom = new ArrayList<>();
            if (saved.getUserId() != null) {
                whom.add(saved.getUserId().toString());
            }
            if (saved.getDriverId() != null) {
                whom.add(saved.getDriverId().toString());
            }
            socketToMultipleUsers(whom, RideConstants.SK_RIDE_START_RESPONSE, saved);
            notifications.sendNotification(Map.of("status", "startride"));
            driverUpdates.onUpdateDriver(Map.of("driverId", String.valueOf(saved.getDriverId()), "tripId", id,
                    "status", RideConstants.DRIVER_STATUS_RIDE_START));
        } catch (Exception e) {
            log.error("Error on startRide----> {}", e.getMessage());
        }
    }

    private static int nz0(Integer v) {
        return v == null ? 0 : v;
    }

    // ================================================================= dropoffRide

    @Transactional
    public Map<String, Object> dropoffRide(String idStr, String driverIdStr) {
        UUID id = uuid(idStr);
        UUID driverId = uuid(driverIdStr);
        Ride ride = requireRide(id);
        if (!"local".equals(ride.getRideType())) {
            throw new ApiException(400, "Invalid ride type");
        }
        if (ride.getDriverId() == null || !ride.getDriverId().equals(driverId)) {
            throw new ApiException(401, "Unauthorized driver");
        }
        if ("endtrip".equals(ride.getStatus())) {
            throw new ApiException(400, "Ride already ended");
        }

        List<RideGps> gpsPoints = rideGpsRepository
                .findByRideIdAndDriverIdAndUserIdOrderByRecordedAtAsc(id, driverId, ride.getUserId());
        double actualKm = 0;
        for (int i = 1; i < gpsPoints.size(); i++) {
            actualKm += haversineMeters(gpsPoints.get(i - 1), gpsPoints.get(i)) / 1000.0;
        }
        actualKm = Math.round(actualKm * 100) / 100.0;

        int actualDurationSec = nz0(ride.getRideTimeSeconds());
        int actualDurationMin = (int) Math.ceil(actualDurationSec / 60.0);
        double extraKm = Math.max(0, actualKm - d(ride.getKm()));
        int extraTime = Math.max(0, actualDurationMin - sectomin(ride.getRideTimeSeconds()));
        int waitingMinutes = nz0(ride.getWaitingMinute());

        Price priceDoc = null;
        if (ride.getVehicleVariantId() != null) {
            VehicleVariant variant = vehicleVariantRepository.findById(ride.getVehicleVariantId()).orElse(null);
            if (variant != null && variant.getLocalPriceId() != null) {
                priceDoc = priceRepository.findById(variant.getLocalPriceId()).filter(p -> !p.isDeleted()).orElse(null);
            }
        }
        if (priceDoc == null) {
            return null; // legacy: `if (!variant) return;`
        }

        ObjectNode finalPrice = calculateFinalLocalFare(ride.getTotalPrice(), priceDoc, extraKm, extraTime,
                waitingMinutes, ride.getUserId());

        ride.setStatus("dropoff");
        ride.setKm(actualKm > d(ride.getKm()) ? BigDecimal.valueOf(actualKm) : ride.getKm());
        ride.setRideTimeSeconds(actualDurationSec);
        ride.setTotalPrice(finalPrice);
        pushProcess(ride, "dropoff");
        Ride saved = rideRepository.save(ride);

        Map<String, Object> rideData = getRideDataById(id, null);
        List<String> whom = new ArrayList<>();
        if (ride.getUserId() != null) {
            whom.add(ride.getUserId().toString());
        }
        if (ride.getDriverId() != null) {
            whom.add(ride.getDriverId().toString());
        }
        socketToMultipleUsers(whom, RideConstants.SK_RIDE_DROPOFF_RESPONSE, rideData);
        notifications.sendNotification(Map.of("status", "dropoff"));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ride", saved);
        return out;
    }

    /** For the pub/sub form (pubsub:ride:update:dropoff carries {_id, driverId, userId}). */
    public void dropoffRide(JsonNode payload) {
        try {
            dropoffRide(payload.path("_id").asText(null), payload.path("driverId").asText(null));
        } catch (Exception e) {
            log.error("Error on dropoffRide (pubsub)----> {}", e.getMessage());
        }
    }

    private static double haversineMeters(RideGps a, RideGps b) {
        double lat1 = a.getLatitude() == null ? 0 : a.getLatitude();
        double lon1 = a.getLongitude() == null ? 0 : a.getLongitude();
        double lat2 = b.getLatitude() == null ? 0 : b.getLatitude();
        double lon2 = b.getLongitude() == null ? 0 : b.getLongitude();
        double r = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(h));
    }

    /** Port of ride.service.js calculateFinalLocalFare. */
    private ObjectNode calculateFinalLocalFare(JsonNode existingPrice, Price priceDoc, double extraKm,
            double extraTime, double waitingMinutes, UUID userId) {
        JsonNode ex = existingPrice == null ? mapper.createObjectNode() : existingPrice;
        double extraKmAmount = extraKm * d(priceDoc.getPricePerExtraKm());
        double extraTimeAmount = extraTime * d(priceDoc.getPricePerExtraMin());
        double waitingAmount = waitingMinutes * d(priceDoc.getWaitingChargePerMin());
        double extraAmount = extraKmAmount + extraTimeAmount + waitingAmount;

        double total = fix2(ex.path("total").asDouble(0) + extraAmount).doubleValue();
        double commissionPercent = d(priceDoc.getCommissionPercent());
        double commissionValue = fix2(total * commissionPercent / 100).doubleValue();
        double subTotal = fix2(total + commissionValue).doubleValue();
        double gstPercent = d(priceDoc.getGstPercent());
        double gstPrice = fix2(subTotal * gstPercent / 100).doubleValue();
        double grandTotal = fix2(subTotal + gstPrice).doubleValue();

        BigDecimal pending = userId == null ? BigDecimal.ZERO : rideRepository.sumPendingCancelAmounts(userId);
        double totalPendingAmount = d(pending);
        double finalGrandTotal = fix2(grandTotal + totalPendingAmount).doubleValue();

        ObjectNode out = ex.deepCopy() instanceof ObjectNode o ? o : mapper.createObjectNode();
        out.put("extraKm", extraKm);
        out.put("extraTime", extraTime);
        out.put("waitingMinutes", waitingMinutes);
        out.put("extraKmAmount", extraKmAmount);
        out.put("extraTimeAmount", extraTimeAmount);
        out.put("waitingAmount", waitingAmount);
        out.put("total", total);
        out.put("commission", commissionValue);
        out.put("subtotal", subTotal);
        out.put("gstPrice", gstPrice);
        out.put("cancelAmountOnRide", totalPendingAmount);
        out.put("grandTotal", finalGrandTotal);
        return out;
    }

    // ================================================================= ride_completePayment (pubsub)

    @Transactional
    public void rideCompletePayment(JsonNode body) {
        try {
            UUID id = uuidOrNull(body.path("_id"));
            String merchantTransactionId = body.path("merchantTransactionId").asText(null);
            JsonNode transactionHistory = body.get("transactionHistory");
            boolean waitingamount = body.hasNonNull("waitingamount") && !body.path("waitingamount").asText("").isEmpty();
            String paymentMethod = body.path("paymentMethod").asText(null);
            String status = "completepayment";

            Map<String, Object> rideData = getRideDataById(id, null);
            if (rideData == null) {
                return;
            }
            Ride ride = requireRide(id);

            double grandTotal = ride.getTotalPrice() == null ? 0 : ride.getTotalPrice().path("grandTotal").asDouble(0);

            // wallet payment
            if ("wallet".equals(paymentMethod) && grandTotal > 0) {
                JsonNode walletResp = axios.updateUserWallet(Map.of("userId", ride.getUserId(), "amount", -grandTotal));
                if (walletResp == null || walletResp.path("success").asBoolean(true) == false) {
                    if (ride.getUserId() != null) {
                        socket.toUser(ride.getUserId().toString(), RideConstants.SK_RIDE_PAYMENT_RESPONSE,
                                Map.of("status", false, "message",
                                        "Insufficient wallet balance or failed to deduct wallet amount"));
                    }
                    return;
                }
            }

            ride.setStatus(status);
            if (merchantTransactionId != null) {
                ride.setMerchantTransactionId(merchantTransactionId);
            }
            if (transactionHistory != null) {
                ride.setTransactionHistory(transactionHistory);
            }
            if (paymentMethod != null) {
                ride.setPaymentMethod(paymentMethod);
            }
            if (waitingamount) {
                ride.setWaitingAmountPaid(true);
            }
            pushProcess(ride, status);

            // cancel-amount-on-ride sweep
            double rideCancelAmt = ride.getTotalPrice() == null ? 0
                    : ride.getTotalPrice().path("cancelAmountOnRide").asDouble(0);
            if (rideCancelAmt > 0 && ride.getUserId() != null) {
                rideRepository.markCancelAmountsPaid(ride.getUserId());
            }

            // ===== referral / wallet distribution (port of ride_completePayment) =====
            List<RidePayment> paymentRecords = new ArrayList<>();
            double adminWalletAmount = 0;
            double driverFees = 0;
            if (grandTotal > 0) {
                JsonNode settings = axios.getSettings().path("data");
                double adminPercent = settings.path("admin_percent").asDouble(0);
                double adminFees = Math.max(adminPercent / 100 * grandTotal, 0);
                driverFees = grandTotal - adminFees;

                double driverReferralCommission = 0;
                double userReferralCommission = 0;

                JsonNode referredDriver = axios.getDriverById(ride.getDriverId());
                JsonNode referredDriverData = referredDriver.path("data").has("driver")
                        ? referredDriver.path("data").path("driver") : referredDriver.path("data");
                String driverReferrerId = referredDriverData.path("referredBy").has("_id")
                        ? referredDriverData.path("referredBy").path("_id").asText(null)
                        : referredDriverData.path("referredBy").asText(null);
                double driverReferralPercent = settings.path("driver_referral_revenue_percent").asDouble(0);
                if (!settings.path("referral_enabled").asBoolean(true) == false
                        && driverReferrerId != null && !driverReferrerId.isBlank()
                        && driverReferralPercent > 0 && adminFees > 0) {
                    driverReferralCommission = fix2(adminFees * driverReferralPercent / 100).doubleValue();
                    if (driverReferralCommission > 0) {
                        UUID refId = safeUuid(driverReferrerId);
                        if (refId != null && ridePaymentRepository
                                .findByRideIdAndActionAndPaymentUserId(ride.getId(), "driverreferralfees", refId).isEmpty()) {
                            paymentRecords.add(RidePayment.builder().rideId(ride.getId())
                                    .action("driverreferralfees").paymentTo("driver")
                                    .amount(fix2(driverReferralCommission)).claimed(false)
                                    .paymentUserId(refId).build());
                        }
                    }
                }

                String userReferralRewardType = settings.path("customer_referral_reward_type").asText(null);
                double userReferralPercent = settings.path("customer_referral_discount_percent").asDouble(0);
                if (settings.path("referral_enabled").asBoolean(true)
                        && "wallet_credit".equals(userReferralRewardType) && userReferralPercent > 0 && adminFees > 0) {
                    JsonNode referredUser = axios.getUserById(ride.getUserId());
                    JsonNode referredUserData = referredUser.path("data").has("user")
                            ? referredUser.path("data").path("user") : referredUser.path("data");
                    String userReferrerId = referredUserData.path("referredBy").has("_id")
                            ? referredUserData.path("referredBy").path("_id").asText(null)
                            : referredUserData.path("referredBy").asText(null);
                    if (userReferrerId != null && !userReferrerId.isBlank()) {
                        UUID refId = safeUuid(userReferrerId);
                        boolean alreadyRewarded = refId != null && !ridePaymentRepository
                                .findByPaymentUserIdAndAction(refId, "userreferralfees").isEmpty();
                        if (refId != null && !alreadyRewarded) {
                            userReferralCommission = fix2(adminFees * userReferralPercent / 100).doubleValue();
                            if (userReferralCommission > 0) {
                                paymentRecords.add(RidePayment.builder().rideId(ride.getId())
                                        .action("userreferralfees").paymentTo("user")
                                        .amount(fix2(userReferralCommission)).claimed(false)
                                        .paymentUserId(refId).build());
                            }
                        }
                    }
                }

                adminWalletAmount = Math.max(adminFees - (driverReferralCommission + userReferralCommission), 0);

                double driverWalletAmount = "cash".equals(paymentMethod) ? -adminFees : driverFees;

                paymentRecords.add(RidePayment.builder().rideId(ride.getId()).action("driverfees")
                        .paymentTo("driver").amount(fix2(driverFees)).claimed(false)
                        .paymentUserId(ride.getDriverId()).build());
                paymentRecords.add(RidePayment.builder().rideId(ride.getId()).action("adminfees")
                        .paymentTo("admin").amount(fix2(adminWalletAmount)).claimed(false)
                        .paymentUserId(safeUuid(((Map<?, ?>) rideData).get("adminId") instanceof JsonNode a
                                ? a.path("_id").asText(null) : null)).build());

                axios.updateDriverWallet(Map.of("driverData", List.of(
                        Map.of("currentAmount", driverWalletAmount, "driverId", String.valueOf(ride.getDriverId())))));
            }

            // adminPayment / driverPayment child rows (ride.schema.js embedded arrays)
            ride.getAdminPayment().add(com.ridehail.rideservice.entity.RideAdminPayment.builder()
                    .ride(ride).transaction(transactionHistory == null ? mapper.createObjectNode() : transactionHistory)
                    .amount(fix2(adminWalletAmount)).build());
            ride.getDriverPayment().add(com.ridehail.rideservice.entity.RideDriverPayment.builder()
                    .ride(ride).transaction(transactionHistory == null ? mapper.createObjectNode() : transactionHistory)
                    .amount(fix2(driverFees)).build());

            rideRepository.save(ride);
            if (!paymentRecords.isEmpty()) {
                ridePaymentRepository.saveAll(paymentRecords);
            }

            // socket / notification
            List<String> whom = new ArrayList<>();
            if (ride.getUserId() != null) {
                whom.add(ride.getUserId().toString());
            }
            if (ride.getDriverId() != null) {
                whom.add(ride.getDriverId().toString());
            }
            socketToMultipleUsers(whom, RideConstants.SK_RIDE_PAYMENT_RESPONSE, rideData);
            notifications.sendNotification(Map.of("status", status));
        } catch (Exception e) {
            log.error("Error on ride_completePayment----> {}", e.getMessage(), e);
        }
    }

    private static UUID safeUuid(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ================================================================= endRide (pubsub)

    @Transactional
    public void endRide(JsonNode payload) {
        try {
            UUID id = uuidOrNull(payload.path("_id"));
            Ride ride = rideRepository.findById(id).orElseThrow();
            ride.setStatus("endtrip");
            pushProcess(ride, "endtrip");
            Ride saved = rideRepository.save(ride);

            List<String> whom = new ArrayList<>();
            if (saved.getUserId() != null) {
                whom.add(saved.getUserId().toString());
            }
            if (saved.getDriverId() != null) {
                whom.add(saved.getDriverId().toString());
            }
            socketToMultipleUsers(whom, RideConstants.SK_RIDE_ENDTRIP_RESPONSE, saved);
            notifications.sendNotification(Map.of("status", "endtrip"));
            driverUpdates.onUpdateDriver(Map.of("driverId", String.valueOf(saved.getDriverId()), "tripId", id,
                    "status", RideConstants.DRIVER_STATUS_RIDE_END));
        } catch (Exception e) {
            log.error("Error on endRide----> {}", e.getMessage());
        }
    }

    // ================================================================= updateVehicleType (pubsub)

    /**
     * Partial port of ride.controller.js updateVehicleType. The legacy function has two branches
     * (update existing "create" ride vs create a brand-new ride) and calls nearby_driversAvailable
     * (a different bucket's function). Here only the primary path - update an existing create-state
     * ride's vehicle type/variant and re-broadcast to drivers - is ported; see summary notes.
     */
    @Transactional
    public void updateVehicleType(JsonNode body) {
        try {
            UUID id = uuidOrNull(body.path("_id"));
            String vehicleType = body.path("vehicleType").asText(null);
            String variantId = body.path("vehiclevarientid").asText(null);
            Ride ride = rideRepository.findById(id).filter(r -> "create".equals(r.getStatus())).orElse(null);
            if (ride == null) {
                return;
            }
            ride.setVehicleTypeId("bookany".equals(vehicleType) || vehicleType == null ? null : safeUuid(vehicleType));
            ride.setVehicleVariantId(safeUuid(variantId));
            ride.setStatus("create");
            pushProcess(ride, "create");
            Ride saved = rideRepository.save(ride);

            JsonNode userResp = axios.getUserById(saved.getUserId());
            JsonNode finalData = userResp.path("data");
            Map<String, Object> userData = new LinkedHashMap<>();
            userData.put("user", finalData);
            userData.put("rideDetails", saved);
            if (saved.getUserId() != null) {
                socket.toUser(saved.getUserId().toString(), RideConstants.SK_UPDATE_VEHICLETYPE, userData);
            }
            notifications.sendNotification(new java.util.HashMap<>(Map.of("status", "updatevechicletype",
                    "userId", String.valueOf(finalData.path("user").path("_id").asText("")))));
        } catch (Exception e) {
            log.error("Error on updateVehicleType----> {}", e.getMessage());
        }
    }

    // ================================================================= cancelRideByUser

    @Transactional
    public Map<String, Object> cancelRideByUser(String idStr, String status, String reason) {
        UUID id = uuid(idStr);
        Ride ride = rideRepository.findById(id).orElseThrow(() -> new ApiException(404, "Ride not found"));
        if ("user_cancel".equals(ride.getStatus())) {
            return Map.of("message", "Ride already cancelled");
        }
        if (!RideConstants.CANCEL_ALLOWED_STATUSES.contains(ride.getStatus())) {
            throw new ApiException(400, "Ride cannot be cancelled in current status");
        }

        boolean chargeable = !RideConstants.NON_CHARGEABLE_CANCEL_STATUSES.contains(ride.getStatus());
        long cancelMinutes = chargeable ? calculateMinutesAbs(System.currentTimeMillis(),
                ride.getUpdatedAt() == null ? System.currentTimeMillis()
                        : ride.getUpdatedAt().toInstant().toEpochMilli()) : 0;
        BigDecimal cancelAmount = chargeable ? resolveCancellationAmount(ride, cancelMinutes) : BigDecimal.ZERO;

        ride.setStatus("user_cancel");
        ride.setCanceledBy("user");
        ride.setReason(reason == null ? "" : reason);
        ride.setCancelMinutes((int) cancelMinutes);
        ride.setCancelAmount(cancelAmount);
        ride.setCancelAmountPaid(false);
        pushProcess(ride, "user_cancel");
        Ride saved = rideRepository.save(ride);

        List<String> drivers = new ArrayList<>();
        if (ride.getRequestedDrivers() != null) {
            for (UUID u : ride.getRequestedDrivers()) {
                drivers.add(u.toString());
            }
        }
        List<String> whom = new ArrayList<>();
        if (ride.getUserId() != null) {
            whom.add(ride.getUserId().toString());
        }
        if (ride.getDriverId() != null) {
            whom.add(ride.getDriverId().toString());
            axios.driverUpdateProfileRaw(Map.of("driverId", ride.getDriverId().toString(),
                    "onRide", false, "driverStatus", "online"));
        }
        Map<String, Object> msg = Map.of("rideDetails", saved, "message", "This ride is cancelled by user");
        socketToMultipleUsers(whom, RideConstants.SK_CANCEL_TRIP_BY_USER, msg);
        socketToMultipleUsers(drivers, RideConstants.SK_CANCEL_TRIP_BY_USER, msg);
        notifications.sendNotification(new java.util.HashMap<>(Map.of("status", "cancelbyuser",
                "userId", String.valueOf(ride.getUserId()))));

        return Map.of("message", "Ride cancelled successfully");
    }

    public void cancelRideByUser(JsonNode payload) {
        try {
            cancelRideByUser(payload.path("_id").asText(null), payload.path("status").asText(null),
                    payload.path("reason").asText(""));
        } catch (Exception e) {
            log.error("Error on cancelRideByUser (pubsub)----> {}", e.getMessage());
        }
    }

    public Map<String, Object> cancelRideByUser(CancelRideByUserRequest req) {
        return cancelRideByUser(req.get_id(), req.getStatus(), req.getReason());
    }

    /** helpers/ride.commonFunction.js calculateminutes. */
    private static long calculateMinutesAbs(long d1, long d2) {
        double diff = (d1 - d2) / 1000.0 / 60.0;
        return Math.round(Math.abs(diff));
    }

    /**
     * Port of ride.controller.js resolveRideCancellationAmount + getRideCancellationPriceConfig +
     * calculateLocalCancellationAmount (local ride path; outstation returns the flat cancel charge).
     */
    private BigDecimal resolveCancellationAmount(Ride ride, long cancelMinutes) {
        double charge;
        double timeLimit;
        if ("outstation".equals(ride.getRideType())) {
            Price priceDoc = ride.getPriceId() == null ? null
                    : priceRepository.findById(ride.getPriceId()).orElse(null);
            if (priceDoc == null && ride.getVehicleVariantId() != null) {
                VehicleVariant v = vehicleVariantRepository.findById(ride.getVehicleVariantId()).orElse(null);
                priceDoc = v != null && v.getOutstationPriceId() != null
                        ? priceRepository.findById(v.getOutstationPriceId()).orElse(null) : null;
            }
            charge = priceDoc == null ? 0 : d(priceDoc.getOutstationCancelCharge());
            return fix2(charge);
        }

        double tpCharge = ride.getTotalPrice() == null ? 0
                : ride.getTotalPrice().path("usercancellationcharges").asDouble(0);
        double tpLimit = ride.getTotalPrice() == null ? 0
                : ride.getTotalPrice().path("usercancellationtimelimit").asDouble(0);
        if (tpCharge > 0 || tpLimit > 0) {
            charge = tpCharge;
            timeLimit = tpLimit;
        } else if (ride.getVehicleVariantId() != null) {
            VehicleVariant v = vehicleVariantRepository.findById(ride.getVehicleVariantId()).orElse(null);
            Price p = v != null && v.getLocalPriceId() != null
                    ? priceRepository.findById(v.getLocalPriceId()).orElse(null) : null;
            charge = p == null ? 0 : d(p.getUserCancellationCharge());
            timeLimit = p == null ? 0 : nz0(p.getUserCancellationTimeLimitMinutes());
        } else {
            return BigDecimal.ZERO;
        }

        if (cancelMinutes <= 0 || charge <= 0) {
            return BigDecimal.ZERO;
        }
        if (timeLimit > 0) {
            return fix2(charge * cancelMinutes / timeLimit);
        }
        return fix2(charge);
    }

    // ================================================================= rideCancelByDriver (pubsub)

    @Transactional
    public void rideCancelByDriver(JsonNode payload) {
        try {
            UUID id = uuidOrNull(payload.path("_id"));
            String reason = payload.path("reason").asText("");
            Ride ride = rideRepository.findById(id).orElse(null);
            if (ride == null || "driver_cancel".equals(ride.getStatus())) {
                return;
            }
            if (!RideConstants.CANCEL_ALLOWED_STATUSES.contains(ride.getStatus())) {
                return;
            }
            boolean chargeable = !RideConstants.NON_CHARGEABLE_CANCEL_STATUSES.contains(ride.getStatus());
            long cancelMinutes = chargeable ? calculateMinutesAbs(System.currentTimeMillis(),
                    ride.getUpdatedAt() == null ? System.currentTimeMillis()
                            : ride.getUpdatedAt().toInstant().toEpochMilli()) : 0;
            BigDecimal cancelAmount = chargeable ? resolveCancellationAmount(ride, cancelMinutes) : BigDecimal.ZERO;

            ride.setStatus("driver_cancel");
            ride.setCanceledBy("driver");
            ride.setReason(reason);
            ride.setCancelMinutes((int) cancelMinutes);
            ride.setCancelAmount(cancelAmount);
            ride.setCancelAmountPaid(false);
            pushProcess(ride, "driver_cancel");
            Ride saved = rideRepository.save(ride);

            if (saved.getDriverId() != null) {
                axios.updateDriverProfile(Map.of("driverId", saved.getDriverId().toString(),
                        "onRide", false, "driverStatus", "online"));
                List<String> whom = List.of(String.valueOf(saved.getUserId()), saved.getDriverId().toString());
                socketToMultipleUsers(whom, RideConstants.SK_CANCEL_TRIP_BY_DRIVER_SEND_USER,
                        Map.of("ride", saved, "message", "This ride is cancelled by driver"));
                notifications.sendNotification(new java.util.HashMap<>(Map.of("status", "cancelbydriver",
                        "userId", String.valueOf(saved.getUserId()))));
            }
        } catch (Exception e) {
            log.error("Error on rideCancelByDriver----> {}", e.getMessage());
        }
    }

    // ================================================================= fetchRide (pubsub)

    /**
     * Partial port of ride.controller.js fetchRide: refreshes the ride's requestedDrivers from a
     * fresh nearby-driver lookup and re-broadcasts. nearby_driversAvailable belongs to another
     * bucket, so requestedDrivers is left unchanged here and only the re-broadcast is performed;
     * see summary notes.
     */
    @Transactional
    public void fetchRide(JsonNode payload) {
        try {
            UUID id = uuidOrNull(payload.path("_id"));
            Ride ride = rideRepository.findById(id).orElse(null);
            if (ride == null) {
                return;
            }
            if (ride.getUserId() != null) {
                socket.toUser(ride.getUserId().toString(), RideConstants.SK_REQUEST_DRIVER_FOR_TRIP,
                        Map.of("rideDetails", ride));
            }
        } catch (Exception e) {
            log.error("Error on fetchRide----> {}", e.getMessage());
        }
    }

    // ================================================================= updateIgnoredDriver (pubsub)

    @Transactional
    public void updateIgnoredDriver(JsonNode payload) {
        try {
            UUID id = uuidOrNull(payload.path("_id"));
            UUID driverId = uuidOrNull(payload.path("driver").path("_id"));
            if (id == null || driverId == null) {
                return;
            }
            Ride ride = rideRepository.findById(id)
                    .filter(r -> "create".equals(r.getStatus()) || "cancelbyuser".equals(r.getStatus()))
                    .orElse(null);
            if (ride == null) {
                return;
            }
            List<UUID> ignore = new ArrayList<>();
            if (ride.getIgnoredDrivers() != null) {
                for (UUID u : ride.getIgnoredDrivers()) {
                    ignore.add(u);
                }
            }
            if (!ignore.contains(driverId)) {
                ignore.add(driverId);
            }
            ride.setIgnoredDrivers(ignore.toArray(UUID[]::new));
            pushProcess(ride, ride.getStatus());
            Ride saved = rideRepository.save(ride);

            if (saved.getUserId() != null) {
                socket.toUser(saved.getUserId().toString(), RideConstants.SK_NO_DRIVER,
                        Map.of("message", "No driver available", "rideDetails", saved));
            }
            notifications.sendNotification(new java.util.HashMap<>(Map.of("status", "nodriver",
                    "userId", String.valueOf(saved.getUserId()))));
        } catch (Exception e) {
            log.error("Error on updateIgnoredDriver----> {}", e.getMessage());
        }
    }

    // ================================================================= suddenendByUser (pubsub)

    @Transactional
    public void suddenendByUser(JsonNode payload) {
        try {
            UUID id = uuidOrNull(payload.path("_id"));
            String status = payload.path("status").asText(null);
            Ride ride = rideRepository.findById(id).orElse(null);
            if (ride == null) {
                return;
            }
            String target = null;
            if ("create".equals(ride.getStatus())) {
                target = "cancel";
            } else if (List.of("accept", "pickup", "startride").contains(ride.getStatus())) {
                target = "suddenend".equals(status) ? "cancelbydriver" : "cancelbyuser";
            } else if ("dropoff".equals(ride.getStatus())) {
                target = "suddenend".equals(status) ? "cancelbydriveronride" : "cancelbyuseronride";
            }
            if (target == null) {
                return;
            }
            ride.setStatus(target);
            pushProcess(ride, target);
            Ride saved = rideRepository.save(ride);

            if (saved.getDriverId() != null) {
                axios.driverUpdateProfileRaw(Map.of("driverId", saved.getDriverId().toString(), "onRide", false));
            }

            if ("cancel".equals(saved.getStatus())) {
                List<String> reqDrivers = new ArrayList<>();
                if (saved.getRequestedDrivers() != null) {
                    for (UUID u : saved.getRequestedDrivers()) {
                        reqDrivers.add(u.toString());
                    }
                }
                socketToMultipleUsers(reqDrivers, RideConstants.SK_CANCEL_TRIP_BY_USER,
                        Map.of("ride", saved, "message", "This ride is cancelled"));
                notifications.sendNotification(new java.util.HashMap<>(Map.of("status", "cancel",
                        "userId", String.valueOf(saved.getUserId()))));
            } else {
                List<String> whom = List.of(String.valueOf(saved.getUserId()), String.valueOf(saved.getDriverId()));
                socketToMultipleUsers(whom, RideConstants.SK_CANCEL_TRIP_SUDDENLY,
                        Map.of("By", "suddenend".equals(status) ? "driver" : "user", "ride", saved));
                notifications.sendNotification(new java.util.HashMap<>(Map.of("status", "cancelbydriver",
                        "userId", String.valueOf(saved.getUserId()))));
            }
        } catch (Exception e) {
            log.error("Error on suddenendByUser----> {}", e.getMessage());
        }
    }
}
