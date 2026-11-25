package com.ridehail.userservice.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.userservice.dto.ExportReportResult;
import com.ridehail.userservice.dto.GetUserNotificationRequest;
import com.ridehail.userservice.dto.GetUsersListRequest;
import com.ridehail.userservice.dto.GetUsersRideReportRequest;
import com.ridehail.userservice.entity.Address;
import com.ridehail.userservice.entity.User;
import com.ridehail.userservice.repository.AddressRepository;
import com.ridehail.userservice.repository.UserRepository;
import com.ridehail.userservice.repository.UserRepository.UsersReportProjection;

/**
 * Bucket "admin-reporting-notifications": bulk/admin-facing user listing, search, CSV/PDF/Excel
 * export, dashboard report aggregates, and notification-target lookups by FCM token. Direct port
 * of user.controller.js#get_users_list/get_users_report/get_users_ride_report/get_users_details/
 * export_report/get_user_notification (service/user.services.js's getUsers/getUserCount/
 * userAggregate/findPopulateUser helpers).
 */
@Service
public class AdminUserReportService {

    private static final PDFont PDF_HEADER_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDFont PDF_BODY_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont PDF_TITLE_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final ObjectMapper objectMapper;

    public AdminUserReportService(UserRepository userRepository, AddressRepository addressRepository, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------
    // get_users_list
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Map<String, Object> getUsersList(GetUsersListRequest req) {
        Specification<User> spec = Specification.where(null);

        if (req.getUserId() != null && !req.getUserId().isBlank()) {
            UUID userId = tryParseUuid(req.getUserId().trim());
            if (userId == null) {
                // Mongoose would throw a CastError for a malformed ObjectId string here, which
                // the original function's own try/catch turns into a generic 500 - reproduced by
                // simply never matching any row rather than crashing the request.
                spec = spec.and((root, q, cb) -> cb.isNull(root.get("id")));
            } else {
                UUID finalUserId = userId;
                spec = spec.and((root, q, cb) -> cb.equal(root.get("id"), finalUserId));
            }
        }

        if (req.getPhoneNumbers() != null && !req.getPhoneNumbers().isEmpty()) {
            List<String> normalized = req.getPhoneNumbers().stream()
                    .map(num -> "+91" + String.valueOf(num).trim())
                    .toList();
            spec = spec.and((root, q, cb) -> root.get("mobile").in(normalized));
        }

        if (req.getSearch() != null && !req.getSearch().trim().isEmpty()) {
            String pattern = "%" + req.getSearch().trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("fullname")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern),
                    cb.like(cb.lower(root.get("mobile")), pattern)));
        }

        if (Boolean.TRUE.equals(req.getIsBlocked())) {
            spec = spec.and((root, q, cb) -> cb.isTrue(root.get("deleted")));
        }

        GetUsersListRequest.Options options = req.getOptions();
        OffsetDateTime fromDate = options != null ? toUtcMidnight(options.getFromDate(), true) : null;
        OffsetDateTime toDate = options != null ? toUtcMidnight(options.getToDate(), false) : null;
        if (fromDate != null) {
            OffsetDateTime finalFromDate = fromDate;
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), finalFromDate));
        }
        if (toDate != null) {
            OffsetDateTime finalToDate = toDate;
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), finalToDate));
        }

        Sort sort = Sort.unsorted();
        if (options != null && options.getSort() != null && !options.getSort().isEmpty()) {
            List<Sort.Order> orders = new ArrayList<>();
            options.getSort().forEach((field, direction) ->
                    orders.add(direction != null && direction < 0 ? Sort.Order.desc(field) : Sort.Order.asc(field)));
            sort = Sort.by(orders);
        }

        List<User> matched = userRepository.findAll(spec, sort);

        int skip = options != null && options.getSkip() != null ? Math.max(options.getSkip(), 0) : 0;
        Integer limit = options != null && options.getLimit() != null ? Math.min(options.getLimit(), 100) : null;
        int from = Math.min(skip, matched.size());
        int to = limit != null ? Math.min(from + Math.max(limit, 0), matched.size()) : matched.size();
        List<User> page = matched.subList(from, to);

        if (page.isEmpty()) {
            throw new ApiException(StatusCode.NOT_FOUND, "No users found");
        }

        Map<UUID, List<Address>> addressesByUser = groupAddressesByUser(page.stream().map(User::getId).toList());
        List<Map<String, Object>> users = page.stream()
                .map(u -> toUserFullMap(u, addressesByUser.getOrDefault(u.getId(), List.of()), req.getSelect()))
                .toList();

        long total = userRepository.count(spec);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("users", users);
        data.put("total", total);
        return data;
    }

    // ------------------------------------------------------------------
    // get_users_report
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Map<String, Object> getUsersReport() {
        OffsetDateTime toDate = OffsetDateTime.now();
        OffsetDateTime fromDate = toDate.minusDays(7);

        UsersReportProjection stats = userRepository.getUsersReportStats(fromDate, toDate);
        // Mongo's $group over an empty $match result set yields zero rows (not a single
        // all-zeroes row), which is what the original's `if (!users)` guards against.
        if (stats == null || stats.getTotalUsers() == null || stats.getTotalUsers() == 0L) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Failed to fetch users report");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalUsers", stats.getTotalUsers());
        result.put("deletedUsers", stats.getDeletedUsers());
        result.put("newUsers", stats.getNewUsers());
        result.put("activeUsers", stats.getActiveUsers());
        return result;
    }

    // ------------------------------------------------------------------
    // get_users_ride_report
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Map<String, Object> getUsersRideReport(GetUsersRideReportRequest req) {
        int page = req.getPage() != null && req.getPage() > 0 ? req.getPage() : 1;
        int limit = req.getLimit() != null && req.getLimit() > 0 ? req.getLimit() : 10;
        int skip = (page - 1) * limit;

        Specification<User> spec = (root, q, cb) -> cb.isFalse(root.get("deleted"));

        String searchText = req.getSearchText() != null ? req.getSearchText().trim() : null;
        if (searchText != null && !searchText.isEmpty()) {
            String pattern = "%" + searchText.toLowerCase(Locale.ROOT) + "%";
            // The original $or also matches a "phone" field that doesn't exist anywhere on the
            // Mongoose Users schema (mobile is the real field) - it can never contribute a match,
            // so it is intentionally omitted here rather than reproduced as dead code.
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("firstname")), pattern),
                    cb.like(cb.lower(root.get("lastname")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern)));
        }

        List<User> matched = userRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));
        int from = Math.min(skip, matched.size());
        int to = Math.min(from + limit, matched.size());
        List<User> page1 = matched.subList(from, to);

        if (page1.isEmpty()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Failed to fetch users report");
        }

        Map<UUID, List<Address>> addressesByUser = groupAddressesByUser(page1.stream().map(User::getId).toList());
        List<Map<String, Object>> list = page1.stream()
                .map(u -> toUserRideReportMap(u, addressesByUser.getOrDefault(u.getId(), List.of())))
                .toList();

        long total = userRepository.count(spec);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        return data;
    }

    // ------------------------------------------------------------------
    // get_users_details
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getUsersDetails(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "userIds array is required");
        }

        List<UUID> ids = userIds.stream()
                .map(this::tryParseUuid)
                .filter(java.util.Objects::nonNull)
                .toList();

        List<User> users = ids.isEmpty() ? List.of() : userRepository.findAllById(ids);
        Map<UUID, List<Address>> addressesByUser = groupAddressesByUser(ids);

        Map<String, Object> select = Map.of(
                "firstname", 1, "lastname", 1, "fullname", 1, "mobile", 1, "profile", 1, "email", 1);

        return users.stream()
                .map(u -> toUserFullMap(u, addressesByUser.getOrDefault(u.getId(), List.of()), select))
                .toList();
    }

    // ------------------------------------------------------------------
    // get_user_notification
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getUserNotification(GetUserNotificationRequest req) {
        if (req.getAction() == null || req.getAction().isBlank()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Action query parameter is required");
        }

        Specification<User> spec = (root, q, cb) -> cb.and(
                cb.isNotNull(root.get("fcm")), cb.notEqual(root.get("fcm"), ""));

        Object mobile = req.getMobile();
        boolean hasMobile = mobile != null && !(mobile instanceof String s && s.isBlank());

        if ("individual".equals(req.getAction()) && hasMobile) {
            List<String> mobiles;
            if (mobile instanceof Collection<?> collection) {
                mobiles = collection.stream().map(m -> "+91" + String.valueOf(m).trim()).toList();
            } else {
                // Note: unlike the array branch, the original does NOT trim a single mobile value.
                mobiles = List.of("+91" + mobile);
            }
            spec = spec.and((root, q, cb) -> root.get("mobile").in(mobiles));
        } else if ("all".equals(req.getAction())) {
            spec = spec.and((root, q, cb) -> cb.isNotNull(root.get("mobile")));
        }

        List<User> users = userRepository.findAll(spec);
        Map<UUID, List<Address>> addressesByUser = groupAddressesByUser(users.stream().map(User::getId).toList());
        Map<String, Object> select = Map.of("mobile", 1, "fcm", 1);

        return users.stream()
                .map(u -> toUserFullMap(u, addressesByUser.getOrDefault(u.getId(), List.of()), select))
                .toList();
    }

    // ------------------------------------------------------------------
    // export_report
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ExportReportResult exportReport(String fileType, String search, String genderFilter,
            String blockFilter, String fromDate, String toDate) {
        Specification<User> spec = Specification.where(null);

        String searchValue = search != null ? search.trim() : null;
        if (searchValue != null && !searchValue.isEmpty()) {
            String pattern = "%" + searchValue.toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("firstname")), pattern),
                    cb.like(cb.lower(root.get("lastname")), pattern),
                    cb.like(cb.lower(root.get("mobile")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern)));
        }

        if (genderFilter != null && !genderFilter.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("gender"), genderFilter));
        }

        if (blockFilter != null && !blockFilter.isBlank()) {
            boolean blocked = "true".equalsIgnoreCase(blockFilter);
            spec = spec.and((root, q, cb) -> cb.equal(root.get("deleted"), blocked));
        }

        OffsetDateTime from = toUtcMidnight(fromDate, true);
        OffsetDateTime to = toUtcMidnight(toDate, false);
        if (from != null) {
            OffsetDateTime finalFrom = from;
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), finalFrom));
        }
        if (to != null) {
            OffsetDateTime finalTo = to;
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), finalTo));
        }

        List<User> users = userRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));
        Map<UUID, List<Address>> addressesByUser = groupAddressesByUser(users.stream().map(User::getId).toList());
        List<Map<String, Object>> rows = buildExportRows(users, addressesByUser);

        if (rows.isEmpty()) {
            return ExportReportResult.error("No data to export");
        }

        List<String> columns = new ArrayList<>(rows.get(0).keySet());

        try {
            if ("csv".equals(fileType)) {
                return ExportReportResult.of(buildCsv(rows, columns), "text/csv", "users.csv");
            }
            if ("pdf".equals(fileType)) {
                return ExportReportResult.of(buildPdf(rows, columns), "application/pdf", "users.pdf");
            }
            if ("excel".equals(fileType)) {
                return ExportReportResult.of(buildExcel(rows, columns),
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "users.xlsx");
            }
        } catch (IOException e) {
            throw new ApiException(StatusCode.INTERNAL_SERVER_ERROR, "Failed to generate export file");
        }

        return ExportReportResult.error("Invalid fileType");
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    private Map<UUID, List<Address>> groupAddressesByUser(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return addressRepository.findByUserIdIn(userIds).stream()
                .collect(Collectors.groupingBy(Address::getUserId));
    }

    private UUID tryParseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Reproduces helpers/user.commonFunction.js#toUTCMidnight exactly, including its "+1 day"
     * shift (fromDate -> next day 00:00:00.000 UTC, toDate -> next day 23:59:59.999 UTC) -
     * preserved as-is even though it looks like an off-by-one, per the "don't fix legacy
     * behaviour" rule.
     */
    private OffsetDateTime toUtcMidnight(String value, boolean isFromDate) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Instant instant = parseFlexibleDate(value);
        if (instant == null) {
            return null;
        }
        ZonedDateTime utc = instant.atZone(ZoneOffset.UTC).plusDays(1);
        if (isFromDate) {
            return utc.toLocalDate().atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        }
        return utc.toLocalDate().atTime(23, 59, 59, 999_000_000).atZone(ZoneOffset.UTC).toOffsetDateTime();
    }

    private Instant parseFlexibleDate(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e1) {
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (DateTimeParseException e2) {
                try {
                    return LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant();
                } catch (DateTimeParseException e3) {
                    return null;
                }
            }
        }
    }

    private boolean truthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.doubleValue() != 0;
        }
        if (value instanceof String s) {
            return !s.isEmpty() && !"0".equals(s);
        }
        return true;
    }

    private List<Object> parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Object>>() {
            });
        } catch (IOException e) {
            return List.of();
        }
    }

    // ------------------------------------------------------------------
    // Response shape builders (plain maps, not DTOs, so the field names/casing match the
    // original ad hoc Mongoose document shape exactly - e.g. "_id" not "id", "otpverify" not
    // "otpVerify")
    // ------------------------------------------------------------------

    private Map<String, Object> toAddressFullMap(Address a) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("_id", a.getId());
        map.put("label", a.getLabel());
        map.put("street1", a.getStreet1());
        map.put("street2", a.getStreet2());
        map.put("district", a.getDistrict());
        map.put("landmark", a.getLandmark());
        map.put("type", a.getType());
        map.put("delete", a.getDeleted());
        map.put("userid", a.getUserId());
        putCoordinate(map, a.getCoordinateLat(), a.getCoordinateLng());
        map.put("createdAt", a.getCreatedAt());
        map.put("updatedAt", a.getUpdatedAt());
        return map;
    }

    /** Matches get_users_ride_report's explicit populate select: "label street1 street2 landmark type coordinate district". */
    private Map<String, Object> toAddressRideReportMap(Address a) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("_id", a.getId());
        map.put("label", a.getLabel());
        map.put("street1", a.getStreet1());
        map.put("street2", a.getStreet2());
        map.put("landmark", a.getLandmark());
        map.put("type", a.getType());
        putCoordinate(map, a.getCoordinateLat(), a.getCoordinateLng());
        map.put("district", a.getDistrict());
        return map;
    }

    private void putCoordinate(Map<String, Object> map, Double lat, Double lng) {
        if (lat != null && lng != null) {
            Map<String, Object> coordinate = new LinkedHashMap<>();
            coordinate.put("type", "Point");
            coordinate.put("coordinates", List.of(lng, lat));
            map.put("coordinate", coordinate);
        }
    }

    /**
     * Full user document shape, mirroring what Mongoose's {@code lean()} would return. If
     * {@code select} is a non-empty inclusion-mode projection map, the result is narrowed down to
     * those keys - but {@code _id} and {@code Address} are always kept, exactly like Mongoose
     * always includes {@code _id} by default and always forces populated paths into the
     * projection so population can work (see getUsers() in service/user.services.js, which
     * unconditionally appends "Address" to every populate() call).
     */
    private Map<String, Object> toUserFullMap(User u, List<Address> addresses, Map<String, Object> select) {
        Map<String, Object> full = new LinkedHashMap<>();
        full.put("_id", u.getId());
        full.put("id", u.getId() != null ? u.getId().toString() : null);
        full.put("firstname", u.getFirstname());
        full.put("lastname", u.getLastname());
        full.put("fullname", u.getFullname());
        full.put("fcm", u.getFcm());
        full.put("socketstatus", u.getSocketstatus());
        full.put("state", u.getState());
        full.put("city", u.getCity());
        full.put("termsandserviceaccept", u.getTermsAndServiceAccept());
        full.put("gender", u.getGender());
        full.put("email", u.getEmail());
        full.put("profile", u.getProfile());
        full.put("mobile", u.getMobile());
        full.put("delete", u.getDeleted());
        full.put("userid", u.getUserid());
        full.put("otp", u.getOtp());
        full.put("otpexpire", u.getOtpExpire());
        full.put("otpverify", u.getOtpVerify());
        full.put("Address", addresses.stream().map(this::toAddressFullMap).toList());
        full.put("socketId", u.getSocketId());
        if (u.getCurrentLocationLat() != null && u.getCurrentLocationLng() != null) {
            Map<String, Object> location = new LinkedHashMap<>();
            location.put("type", "Point");
            location.put("coordinates", List.of(u.getCurrentLocationLng(), u.getCurrentLocationLat()));
            full.put("currentLocation", location);
        }
        full.put("tripid", parseJsonArray(u.getTripIds()));
        full.put("emergencyContacts", parseJsonArray(u.getEmergencyContacts()));
        full.put("profileSkip", u.getProfileSkip());

        Map<String, Object> preferences = new LinkedHashMap<>();
        preferences.put("allowSmsInvoice", u.getAllowSmsInvoice());
        preferences.put("allowWhatsAppUpdates", u.getAllowWhatsAppUpdates());
        preferences.put("allowEmailInvoice", u.getAllowEmailInvoice());
        preferences.put("allowMobileNotification", u.getAllowMobileNotification());
        full.put("preferences", preferences);

        Map<String, Object> wallet = new LinkedHashMap<>();
        wallet.put("currentAmount", u.getWalletCurrentAmount());
        wallet.put("lastUpdated", u.getWalletLastUpdated());
        full.put("wallet", wallet);

        full.put("referralCode", u.getReferralCode());
        full.put("referredBy", u.getReferredBy());
        full.put("referrals", parseJsonArray(u.getReferrals()));

        Map<String, Object> referralReward = new LinkedHashMap<>();
        referralReward.put("type", u.getReferralRewardType() == null ? "" : u.getReferralRewardType());
        referralReward.put("amount", u.getReferralRewardAmount());
        referralReward.put("discountPercent", u.getReferralRewardDiscountPercent());
        referralReward.put("remainingTrips", u.getReferralRewardRemainingTrips());
        referralReward.put("usedRideIds", parseJsonArray(u.getReferralRewardUsedRideIds()));
        referralReward.put("grantedAt", u.getReferralRewardGrantedAt());
        full.put("referralReward", referralReward);

        full.put("walletBalance", u.getWalletBalance());
        full.put("createdAt", u.getCreatedAt());
        full.put("updatedAt", u.getUpdatedAt());

        if (select == null || select.isEmpty()) {
            return full;
        }
        boolean inclusionMode = select.values().stream().anyMatch(this::truthy);
        if (!inclusionMode) {
            return full;
        }
        Map<String, Object> filtered = new LinkedHashMap<>();
        filtered.put("_id", full.get("_id"));
        filtered.put("Address", full.get("Address"));
        for (Map.Entry<String, Object> entry : select.entrySet()) {
            if (truthy(entry.getValue()) && full.containsKey(entry.getKey())) {
                filtered.put(entry.getKey(), full.get(entry.getKey()));
            }
        }
        return filtered;
    }

    /** Matches get_users_ride_report's explicit select: _id/profile/firstname/lastname/fullname/mobile/email/createdAt. */
    private Map<String, Object> toUserRideReportMap(User u, List<Address> addresses) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("_id", u.getId());
        map.put("profile", u.getProfile());
        map.put("firstname", u.getFirstname());
        map.put("lastname", u.getLastname());
        map.put("fullname", u.getFullname());
        map.put("mobile", u.getMobile());
        map.put("email", u.getEmail());
        map.put("createdAt", u.getCreatedAt());
        map.put("Address", addresses.stream().map(this::toAddressRideReportMap).toList());
        return map;
    }

    // ------------------------------------------------------------------
    // export_report file builders
    // ------------------------------------------------------------------

    private List<Map<String, Object>> buildExportRows(List<User> users, Map<UUID, List<Address>> addressesByUser) {
        // toLocaleString("en-IN", {...}) with no explicit timeZone renders in the Node process's
        // local timezone - reproduced with the JVM's default zone rather than hardcoding IST.
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy, hh:mm a", Locale.ENGLISH)
                .withZone(ZoneId.systemDefault());

        List<Map<String, Object>> rows = new ArrayList<>();
        for (User u : users) {
            List<Address> addresses = addressesByUser.getOrDefault(u.getId(), List.of());
            Address home = addresses.stream().filter(a -> "home".equals(a.getType())).findFirst().orElse(null);

            String fullAddress = "-";
            if (home != null) {
                List<String> parts = new ArrayList<>();
                addIfPresent(parts, home.getStreet1());
                addIfPresent(parts, home.getStreet2());
                addIfPresent(parts, home.getLandmark());
                // Address has no city/state/country fields in the original Mongoose schema
                // either, so those parts of the original join are always skipped here too.
                addIfPresent(parts, home.getDistrict());
                if (!parts.isEmpty()) {
                    fullAddress = String.join(", ", parts);
                }
            }

            Map<String, Object> row = new LinkedHashMap<>();
            String riderName = joinNonBlank(" ", u.getFirstname(), u.getLastname());
            row.put("Rider Name", riderName.isEmpty() ? "-" : riderName);
            row.put("Gender", blankToDash(u.getGender()));
            row.put("Mail Id", blankToDash(u.getEmail()));
            row.put("Mobile Number", blankToDash(u.getMobile()));
            row.put("Address", fullAddress);
            row.put("State", blankToDash(u.getState()));
            row.put("City", blankToDash(u.getCity()));
            row.put("Register At", u.getCreatedAt() == null ? "-" : formatter.format(u.getCreatedAt()));
            row.put("Block Status", Boolean.TRUE.equals(u.getDeleted()) ? "Blocked" : "Active");
            rows.add(row);
        }
        return rows;
    }

    private void addIfPresent(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value);
        }
    }

    private String joinNonBlank(String delimiter, String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                parts.add(value);
            }
        }
        return String.join(delimiter, parts);
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private byte[] buildCsv(List<Map<String, Object>> rows, List<String> columns) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", columns));
        for (Map<String, Object> row : rows) {
            sb.append("\n");
            String line = columns.stream()
                    .map(col -> "\"" + String.valueOf(row.getOrDefault(col, "")) + "\"")
                    .collect(Collectors.joining(","));
            sb.append(line);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] buildExcel(List<Map<String, Object>> rows, List<String> columns) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Users");

            CellStyle headerStyle = workbook.createCellStyle();
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            headerStyle.setFont(boldFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns.get(i));
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 25 * 256);
            }

            int rowIndex = 1;
            for (Map<String, Object> row : rows) {
                Row xlsxRow = sheet.createRow(rowIndex++);
                for (int i = 0; i < columns.size(); i++) {
                    Object value = row.get(columns.get(i));
                    xlsxRow.createCell(i).setCellValue(value == null ? "" : String.valueOf(value));
                }
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] buildPdf(List<Map<String, Object>> rows, List<String> columns) throws IOException {
        PDRectangle landscapeA4 = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
        float margin = 40f;
        float rowHeight = 20f;

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(landscapeA4);
            document.addPage(page);
            PDPageContentStream contentStream = new PDPageContentStream(document, page);

            float pageContentWidth = landscapeA4.getWidth() - 2 * margin;
            float colWidth = pageContentWidth / columns.size();
            float y = landscapeA4.getHeight() - margin;

            y = drawPdfTitle(contentStream, landscapeA4, y);
            y = drawPdfRow(contentStream, columns, null, margin, y, colWidth, rowHeight, PDF_HEADER_FONT, 8);

            for (Map<String, Object> row : rows) {
                if (y - rowHeight < margin) {
                    contentStream.close();
                    page = new PDPage(landscapeA4);
                    document.addPage(page);
                    contentStream = new PDPageContentStream(document, page);
                    y = landscapeA4.getHeight() - margin;
                    y = drawPdfRow(contentStream, columns, null, margin, y, colWidth, rowHeight, PDF_HEADER_FONT, 8);
                }
                y = drawPdfRow(contentStream, columns, row, margin, y, colWidth, rowHeight, PDF_BODY_FONT, 7);
            }

            contentStream.close();
            document.save(out);
            return out.toByteArray();
        }
    }

    private float drawPdfTitle(PDPageContentStream contentStream, PDRectangle page, float y) throws IOException {
        String title = "User Report";
        float fontSize = 18f;
        float textWidth = PDF_TITLE_FONT.getStringWidth(title) / 1000 * fontSize;
        contentStream.beginText();
        contentStream.setFont(PDF_TITLE_FONT, fontSize);
        contentStream.newLineAtOffset((page.getWidth() - textWidth) / 2, y);
        contentStream.showText(title);
        contentStream.endText();
        return y - 30f;
    }

    /** Draws a header row when {@code row} is null (using {@code columns} as the cell text), otherwise a data row. */
    private float drawPdfRow(PDPageContentStream contentStream, List<String> columns, Map<String, Object> row,
            float margin, float y, float colWidth, float rowHeight, PDFont font, float fontSize) throws IOException {
        float x = margin;
        for (String col : columns) {
            String text = row == null ? col : String.valueOf(row.getOrDefault(col, ""));
            drawCenteredText(contentStream, text, x, y, colWidth, font, fontSize);
            x += colWidth;
        }
        return y - rowHeight;
    }

    private void drawCenteredText(PDPageContentStream contentStream, String text, float x, float y, float width,
            PDFont font, float fontSize) throws IOException {
        String safeText = text == null ? "" : text.replaceAll("[\\r\\n]", " ");
        if (safeText.length() > 60) {
            safeText = safeText.substring(0, 60);
        }
        float textWidth = font.getStringWidth(safeText) / 1000 * fontSize;
        float offset = Math.max((width - textWidth) / 2, 0);
        contentStream.beginText();
        contentStream.setFont(font, fontSize);
        contentStream.newLineAtOffset(x + offset, y);
        contentStream.showText(safeText);
        contentStream.endText();
    }
}
