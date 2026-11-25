package com.ridehail.adminservice.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ridehail.adminservice.entity.AdminActivity;
import com.ridehail.adminservice.entity.Coupon;
import com.ridehail.adminservice.entity.CouponUsage;
import com.ridehail.adminservice.repository.AdminActivityRepository;
import com.ridehail.adminservice.repository.CouponRepository;
import com.ridehail.adminservice.repository.CouponUsageRepository;
import com.ridehail.adminservice.util.InternalMultipartClient;
import com.ridehail.common.constants.AuthUserType;
import com.ridehail.common.security.AuthContext;
import com.ridehail.common.exception.ApiException;
import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.security.InternalServiceClient;

/**
 * Port of the coupon controller functions from Admin-service/controller/admin.controller.js:
 * add_coupon, update_coupon, get_coupon_list, get_coupon, send_coupon, get_user_coupons,
 * consume_coupon. Behaviour (validation order, messages, status codes) is preserved verbatim.
 */
@Service
public class CouponService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final InternalServiceClient internalServiceClient;
    private final InternalMultipartClient multipartClient;
    private final AdminActivityRepository adminActivityRepository;
    private final org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String commonServiceUrl;
    private final String userServiceUrl;

    public CouponService(CouponRepository couponRepository,
                         CouponUsageRepository couponUsageRepository,
                         InternalServiceClient internalServiceClient,
                         InternalMultipartClient multipartClient,
                         AdminActivityRepository adminActivityRepository,
                         org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate,
                         @Value("${ridehail.services.common-url}") String commonServiceUrl,
                         @Value("${ridehail.services.user-url}") String userServiceUrl) {
        this.couponRepository = couponRepository;
        this.couponUsageRepository = couponUsageRepository;
        this.internalServiceClient = internalServiceClient;
        this.multipartClient = multipartClient;
        this.adminActivityRepository = adminActivityRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.commonServiceUrl = commonServiceUrl;
        this.userServiceUrl = userServiceUrl;
    }

    // ------------------------------------------------------------------ add_coupon

    @Transactional
    public Map<String, Object> addCoupon(com.ridehail.adminservice.dto.AddCouponRequest req,
                                         org.springframework.web.multipart.MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Coupon image is required");
        }

        OffsetDateTime startDate = parseDate(req.getStartDate());
        OffsetDateTime expiryDate = parseDate(req.getExpiryDate());
        if (startDate == null || expiryDate == null) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Invalid startDate or expiryDate");
        }

        LocalDate todayIST = OffsetDateTime.now().atZoneSameInstant(IST).toLocalDate();
        LocalDate startIST = startDate.atZoneSameInstant(IST).toLocalDate();
        LocalDate expiryIST = expiryDate.atZoneSameInstant(IST).toLocalDate();

        if (startIST.isBefore(todayIST)) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Start date cannot be in the past");
        }
        if (!expiryIST.isAfter(startIST)) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Expiry date must be greater than start date");
        }

        if (couponRepository.findByCodeAndDeletedFalse(req.getCode()).isPresent()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Coupon code already exists");
        }

        String imgUrl = uploadImage(image, "public_coupon");
        if (imgUrl == null || imgUrl.isBlank()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Failed to upload coupon image");
        }

        Coupon coupon = new Coupon();
        coupon.setTitle(nvl(req.getTitle()));
        coupon.setDescription(nvl(req.getDescription()));
        coupon.setCode(nvl(req.getCode()));
        coupon.setImage(imgUrl);
        coupon.setTripType(req.getTripType() != null ? req.getTripType() : "local");
        coupon.setDiscountType(req.getDiscountType() != null ? req.getDiscountType() : "amount");
        coupon.setDiscountValue(req.getDiscountValue() != null ? req.getDiscountValue() : BigDecimal.ZERO);
        coupon.setStartDate(startDate);
        coupon.setExpiryDate(expiryDate);
        coupon = couponRepository.save(coupon);

        logActivity("add_coupon", null);
        return toMap(coupon);
    }

    // --------------------------------------------------------------- update_coupon

    @Transactional
    public Map<String, Object> updateCoupon(com.ridehail.adminservice.dto.UpdateCouponRequest req,
                                            org.springframework.web.multipart.MultipartFile image) {
        if (req.getCouponId() == null || req.getCouponId().isBlank()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Coupon ID is required");
        }
        UUID couponId = parseUuid(req.getCouponId());
        Coupon coupon = couponId == null ? null
                : couponRepository.findByIdAndDeletedFalse(couponId).orElse(null);
        if (coupon == null) {
            throw new ApiException(StatusCode.NOT_FOUND, "Coupon not found");
        }

        boolean deleteRequested = "true".equalsIgnoreCase(String.valueOf(req.getDelete()));
        if (deleteRequested) {
            coupon.setDeleted(true);
            couponRepository.save(coupon);
            logActivity("delete_coupon", null);
            return toMap(coupon);
        }

        if (req.getStartDate() != null || req.getExpiryDate() != null) {
            OffsetDateTime startDate = req.getStartDate() != null ? parseDate(req.getStartDate()) : coupon.getStartDate();
            OffsetDateTime expiryDate = req.getExpiryDate() != null ? parseDate(req.getExpiryDate()) : coupon.getExpiryDate();
            if (startDate == null || expiryDate == null) {
                throw new ApiException(StatusCode.BAD_REQUEST, "Invalid startDate or expiryDate");
            }
            OffsetDateTime now = OffsetDateTime.now();
            if (startDate.isBefore(now)) {
                throw new ApiException(StatusCode.BAD_REQUEST, "Start date cannot be in the past");
            }
            if (!expiryDate.isAfter(startDate)) {
                throw new ApiException(StatusCode.BAD_REQUEST, "Expiry date must be greater than start date");
            }
            coupon.setStartDate(startDate);
            coupon.setExpiryDate(expiryDate);
        }

        if (req.getCode() != null) {
            if (couponRepository.findByCodeAndDeletedFalseAndIdNot(req.getCode(), couponId).isPresent()) {
                throw new ApiException(StatusCode.BAD_REQUEST, "Coupon code already exists");
            }
            coupon.setCode(req.getCode());
        }

        if (image != null && !image.isEmpty()) {
            String imgUrl = uploadImage(image, "public_coupon");
            if (imgUrl == null || imgUrl.isBlank()) {
                throw new ApiException(StatusCode.BAD_REQUEST, "Failed to upload coupon image");
            }
            coupon.setImage(imgUrl);
        }

        if (req.getTitle() != null) coupon.setTitle(req.getTitle());
        if (req.getDescription() != null) coupon.setDescription(req.getDescription());
        if (req.getTripType() != null) coupon.setTripType(req.getTripType());
        if (req.getDiscountType() != null) coupon.setDiscountType(req.getDiscountType());
        if (req.getDiscountValue() != null) coupon.setDiscountValue(req.getDiscountValue());

        coupon = couponRepository.save(coupon);
        logActivity("update_coupon", null);
        return toMap(coupon);
    }

    // ------------------------------------------------------------- get_coupon_list

    @Transactional(readOnly = true)
    public Map<String, Object> getCouponList(Integer page, Integer limit, String search) {
        String term = search == null ? "" : search.trim();

        Specification<Coupon> spec = (root, query, cb) -> {
            if (term.isEmpty()) {
                return cb.conjunction();
            }
            String like = "%" + term.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("code")), like));
        };

        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        List<Coupon> coupons;
        long totalCount;
        if (page != null && limit != null && page > 0 && limit > 0) {
            var pageResult = couponRepository.findAll(spec, PageRequest.of(page - 1, limit, sort));
            coupons = pageResult.getContent();
            totalCount = couponRepository.count(spec);
        } else {
            coupons = couponRepository.findAll(spec, sort);
            totalCount = coupons.size();
        }

        if (coupons.isEmpty()) {
            throw new ApiException(StatusCode.NOT_FOUND, "No coupons found");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", coupons.stream().map(this::toMap).collect(Collectors.toList()));
        result.put("totalCount", totalCount);
        return result;
    }

    // ------------------------------------------------------------------ get_coupon

    @Transactional(readOnly = true)
    public Map<String, Object> getCoupon(String id) {
        UUID couponId = parseUuid(id);
        Coupon coupon = couponId == null ? null : couponRepository.findById(couponId).orElse(null);
        if (coupon == null) {
            throw new ApiException(StatusCode.NOT_FOUND, "Coupon not found");
        }
        return toMap(coupon);
    }

    // ----------------------------------------------------------------- send_coupon

    @Transactional
    public String sendCoupon(com.ridehail.adminservice.dto.SendCouponRequest req) {
        if (req.getCouponId() == null || req.getSendTo() == null) {
            throw new ApiException(StatusCode.BAD_REQUEST, "couponId and sendTo are required");
        }
        UUID couponId = parseUuid(req.getCouponId());
        Coupon coupon = couponId == null ? null
                : couponRepository.findByIdAndDeletedFalse(couponId).orElse(null);
        if (coupon == null) {
            throw new ApiException(StatusCode.NOT_FOUND, "Coupon not found");
        }

        if ("ALL".equals(req.getSendTo())) {
            ObjectNode data = objectMapper.createObjectNode();
            data.put("type", "COUPON");
            data.put("couponId", coupon.getId().toString());
            data.put("title", coupon.getTitle());
            data.put("message", coupon.getDescription());
            data.put("image", coupon.getImage() != null ? coupon.getImage() : "");
            data.set("androidData", objectMapper.createObjectNode().put("couponId", coupon.getId().toString()));
            data.put("sendTo", "ALL");
            publishNotification(data);
            logActivity("send_coupon", Map.of("couponId", coupon.getId().toString(), "sendTo", "ALL"));
            return "Coupon sent to all users";
        }

        if ("INDIVIDUAL".equals(req.getSendTo())) {
            List<String> phoneNumbers = req.getPhoneNumbers() == null ? List.of() : req.getPhoneNumbers();
            if (phoneNumbers.isEmpty()) {
                throw new ApiException(StatusCode.BAD_REQUEST, "Phone numbers are required");
            }
            JsonNode userResponse = internalServiceClient.post(userServiceUrl + "/get-users-list",
                    Map.of("phoneNumbers", phoneNumbers));
            JsonNode usersNode = userResponse == null ? null : userResponse.path("data").path("users");
            List<JsonNode> users = new ArrayList<>();
            if (usersNode != null && usersNode.isArray()) {
                usersNode.forEach(users::add);
            }
            if (users.isEmpty()) {
                throw new ApiException(StatusCode.BAD_REQUEST, "No valid users found");
            }

            List<String> userIds = users.stream()
                    .map(u -> u.path("_id").asText(null))
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());

            UUID[] existing = coupon.getEligibleUsers() != null ? coupon.getEligibleUsers() : new UUID[0];
            java.util.LinkedHashSet<UUID> merged = new java.util.LinkedHashSet<>(java.util.Arrays.asList(existing));
            for (String uid : userIds) {
                UUID parsed = parseUuid(uid);
                if (parsed != null) {
                    merged.add(parsed);
                }
            }
            coupon.setEligibleUsers(merged.toArray(new UUID[0]));
            couponRepository.save(coupon);

            List<String> fcmTokens = users.stream()
                    .map(u -> u.path("fcm").asText(null))
                    .filter(t -> t != null && !t.isBlank())
                    .collect(Collectors.toList());

            ObjectNode data = objectMapper.createObjectNode();
            data.put("type", "COUPON");
            data.put("couponId", coupon.getId().toString());
            data.put("title", coupon.getTitle());
            data.put("message", coupon.getDescription());
            data.put("image", coupon.getImage() != null ? coupon.getImage() : "");
            data.set("androidData", objectMapper.createObjectNode().put("couponId", coupon.getId().toString()));
            data.set("user", objectMapper.valueToTree(fcmTokens));
            data.set("userIds", objectMapper.valueToTree(userIds));
            publishNotification(data);

            logActivity("send_coupon",
                    Map.of("couponId", coupon.getId().toString(), "sendTo", "INDIVIDUAL", "userIds", userIds));
            return "Coupon sent to selected users";
        }

        throw new ApiException(StatusCode.BAD_REQUEST, "Invalid sendTo value");
    }

    // ------------------------------------------------------------ get_user_coupons

    @Transactional(readOnly = true)
    public Object getUserCoupons(com.ridehail.adminservice.dto.GetUserCouponsRequest req) {
        if (req.getUserId() == null || req.getTripType() == null) {
            throw new ApiException(StatusCode.BAD_REQUEST, "userId and tripType are required");
        }
        UUID userId = parseUuid(req.getUserId());
        String couponCode = req.getCouponId();

        List<UUID> usedCouponIds = new ArrayList<>();
        if (userId != null) {
            usedCouponIds = couponUsageRepository.findByUserId(userId).stream()
                    .map(CouponUsage::getCouponId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
        }

        if (couponCode != null && !usedCouponIds.isEmpty()) {
            boolean alreadyUsedByCode = usedCouponIds.stream()
                    .map(UUID::toString)
                    .anyMatch(couponCode::equals);
            if (alreadyUsedByCode) {
                return List.of();
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<Coupon> eligible = couponRepository.findEligibleForUser(
                req.getTripType(), now, userId, couponCode);

        final List<UUID> excluded = usedCouponIds;
        return eligible.stream()
                .filter(c -> !excluded.contains(c.getId()))
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("_id", c.getId().toString());
                    m.put("title", c.getTitle());
                    m.put("description", c.getDescription());
                    m.put("code", c.getCode());
                    m.put("image", c.getImage());
                    m.put("discountType", c.getDiscountType());
                    m.put("discountValue", c.getDiscountValue());
                    m.put("expiryDate", c.getExpiryDate());
                    return m;
                })
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------- consume_coupon

    public record ConsumeResult(String message, Map<String, Object> data) {
    }

    @Transactional
    public ConsumeResult consumeCoupon(com.ridehail.adminservice.dto.ConsumeCouponRequest req) {
        if (req.getCouponId() == null || req.getUserId() == null || req.getRideId() == null) {
            throw new ApiException(StatusCode.BAD_REQUEST, "couponId, userId and rideId are required");
        }
        UUID couponId = parseUuid(req.getCouponId());
        UUID userId = parseUuid(req.getUserId());
        UUID rideId = parseUuid(req.getRideId());

        Coupon coupon = couponId == null ? null : couponRepository.findById(couponId).orElse(null);
        if (coupon == null) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Coupon Invalid");
        }

        Optional<CouponUsage> existing = couponUsageRepository.findByCouponIdAndUserId(couponId, userId);
        if (existing.isPresent()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("alreadyUsed", true);
            return new ConsumeResult("Coupon already used", m);
        }

        CouponUsage usage = new CouponUsage();
        usage.setCouponId(couponId);
        usage.setUserId(userId);
        usage.setRideId(rideId);
        usage.setUsedAt(OffsetDateTime.now());
        usage.setDiscountAmount(req.getDiscountAmount() != null ? req.getDiscountAmount() : BigDecimal.ZERO);
        usage = couponUsageRepository.save(usage);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", usage.getId().toString());
        m.put("couponId", couponId != null ? couponId.toString() : null);
        m.put("userId", userId != null ? userId.toString() : null);
        m.put("rideId", rideId != null ? rideId.toString() : null);
        m.put("discountAmount", usage.getDiscountAmount());
        m.put("usedAt", usage.getUsedAt());
        m.put("createdAt", usage.getCreatedAt());
        m.put("updatedAt", usage.getUpdatedAt());
        return new ConsumeResult("Coupon consumed successfully", m);
    }

    // --------------------------------------------------------------------- helpers

    /** Port of helpers/admin.commonFunction.js#logActivity - best-effort, never fails the request. */
    private void logActivity(String action, Map<String, Object> meta) {
        try {
            AuthContext ctx = AuthContext.current();
            if (ctx == null || ctx.authUserType() != AuthUserType.ADMIN || ctx.principalId() == null) {
                return;
            }
            UUID adminId = parseUuid(ctx.principalId());
            if (adminId == null) {
                return;
            }
            AdminActivity activity = new AdminActivity();
            activity.setAdminId(adminId);
            activity.setAction(action);
            activity.setMeta(objectMapper.valueToTree(meta == null ? Map.of() : meta));
            adminActivityRepository.save(activity);
        } catch (Exception ignored) {
            // matches the original: "Activity Log Error" is only logged, never thrown
        }
    }

    private void publishNotification(JsonNode data) {
        try {
            rabbitTemplate.convertAndSend(
                    com.ridehail.adminservice.config.RabbitConfig.PUSH_NOTIFICATION_QUEUE,
                    objectMapper.convertValue(data, java.util.Map.class));
        } catch (Exception e) {
            // original swallows queue errors (config/rabitmq.js#sendNotification)
        }
    }

    private String uploadImage(org.springframework.web.multipart.MultipartFile file, String sameFolderName) {
        MultiValueMap<String, Object> form = InternalMultipartClient.newForm();
        form.add("imageFiles", InternalMultipartClient.filePart(file));
        form.add("allSame", "true");
        form.add("sameFolderName", sameFolderName);
        JsonNode resp = multipartClient.post(commonServiceUrl + "/image-upload", form);
        if (resp == null || !resp.path("status").asBoolean(false)) {
            return null;
        }
        JsonNode arr = resp.path("data");
        return arr.isArray() && arr.size() > 0 ? arr.get(0).asText(null) : null;
    }

    private Map<String, Object> toMap(Coupon c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", c.getId().toString());
        m.put("title", c.getTitle());
        m.put("description", c.getDescription());
        m.put("code", c.getCode());
        m.put("image", c.getImage());
        m.put("tripType", c.getTripType());
        m.put("discountType", c.getDiscountType());
        m.put("discountValue", c.getDiscountValue());
        m.put("startDate", c.getStartDate());
        m.put("expiryDate", c.getExpiryDate());
        List<String> eligible = new ArrayList<>();
        if (c.getEligibleUsers() != null) {
            for (UUID u : c.getEligibleUsers()) {
                eligible.add(u.toString());
            }
        }
        m.put("eligibleUsers", eligible);
        m.put("delete", c.isDeleted());
        m.put("createdAt", c.getCreatedAt());
        m.put("updatedAt", c.getUpdatedAt());
        return m;
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static UUID parseUuid(String s) {
        if (s == null) {
            return null;
        }
        try {
            return UUID.fromString(s.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static OffsetDateTime parseDate(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(s);
        } catch (Exception ignored) {
            // fall through
        }
        try {
            return java.time.Instant.parse(s).atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
            // fall through
        }
        try {
            return LocalDate.parse(s).atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
            return null;
        }
    }
}
