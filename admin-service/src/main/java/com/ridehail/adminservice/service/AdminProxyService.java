package com.ridehail.adminservice.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ridehail.adminservice.util.DownstreamClient;
import com.ridehail.common.constants.AuthUserType;
import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.constants.StatusResult;
import com.ridehail.common.exception.ApiException;
import com.ridehail.common.response.ApiResponseService;
import com.ridehail.common.security.AuthContext;

/**
 * Cross-service proxy logic for the admin dashboard "reports / fleet / tracking / export" bucket.
 * Direct port of the matching functions in Admin-service/controller/admin.controller.js, where
 * every one of these was an axios call to user/driver/ride/notification/helperdesk/vehicle/socket
 * service (Admin-service/utills/admin.axios.js). No local tables back this bucket.
 */
@Service
public class AdminProxyService {

    private final DownstreamClient http;
    private final ApiResponseService api;
    private final ObjectMapper mapper = new ObjectMapper();

    private final String userUrl;
    private final String driverUrl;
    private final String rideUrl;
    private final String notificationUrl;
    private final String vehicleUrl;
    private final String socketUrl;
    private final String helperdeskUrl;

    public AdminProxyService(
            DownstreamClient http,
            ApiResponseService api,
            @Value("${ridehail.services.user-url}") String userUrl,
            @Value("${ridehail.services.driver-url}") String driverUrl,
            @Value("${ridehail.services.ride-url}") String rideUrl,
            @Value("${ridehail.services.notification-url}") String notificationUrl,
            @Value("${ridehail.services.vehicle-url}") String vehicleUrl,
            @Value("${ridehail.services.socket-url}") String socketUrl,
            @Value("${ridehail.services.helperdesk-url}") String helperdeskUrl) {
        this.http = http;
        this.api = api;
        this.userUrl = userUrl;
        this.driverUrl = driverUrl;
        this.rideUrl = rideUrl;
        this.notificationUrl = notificationUrl;
        this.vehicleUrl = vehicleUrl;
        this.socketUrl = socketUrl;
        this.helperdeskUrl = helperdeskUrl;
    }

    // ------------------------------------------------------------------ helpers

    private static boolean statusOk(JsonNode r) {
        return r != null && r.path("status").asBoolean(false);
    }

    private static String msg(JsonNode r, String def) {
        String m = r == null ? null : r.path("message").asText(null);
        return (m == null || m.isEmpty()) ? def : m;
    }

    private static int code(JsonNode r, int def) {
        return r == null ? def : r.path("code").asInt(def);
    }

    /** {@code resp?.data} or the given fallback when data is missing/null. */
    private JsonNode dataOr(JsonNode r, JsonNode fallback) {
        if (r == null) {
            return fallback;
        }
        JsonNode d = r.get("data");
        return (d == null || d.isNull()) ? fallback : d;
    }

    private ArrayNode emptyArray() {
        return mapper.createArrayNode();
    }

    private ObjectNode emptyObject() {
        return mapper.createObjectNode();
    }

    private ResponseEntity<?> success(int codeVal, String message, Object data) {
        return api.send(true, StatusResult.SUCCESS, codeVal, message, data);
    }

    private ResponseEntity<?> fail(int codeVal, String message, Object data) {
        return api.send(false, StatusResult.ERROR, codeVal, message, data);
    }

    private void requireAdmin(String action) {
        AuthContext ctx = AuthContext.current();
        AuthUserType t = ctx == null ? null : ctx.authUserType();
        if (t != AuthUserType.ADMIN && t != AuthUserType.IN_APP) {
            throw new ApiException(StatusCode.FORBIDDEN, action);
        }
    }

    // ------------------------------------------------------------------ SOS / support / chat

    public ResponseEntity<?> getSos(JsonNode body) {
        ObjectNode payload = emptyObject();
        payload.put("page", body.path("page").asInt(1));
        payload.put("limit", body.path("limit").asInt(20));
        if (body.hasNonNull("userType")) {
            payload.set("userType", body.get("userType"));
        }
        if (body.hasNonNull("sosId")) {
            payload.set("sosId", body.get("sosId"));
        }
        JsonNode resp = http.post(helperdeskUrl + "/sos/list", payload);
        if (!statusOk(resp)) {
            return fail(StatusCode.NOT_FOUND, msg(resp, "Failed to fetch SOS list"), emptyArray());
        }
        return success(StatusCode.SUCCESS, msg(resp, "SOS list fetched successfully"), dataOr(resp, emptyArray()));
    }

    public ResponseEntity<?> getSupport(Map<String, String> query) {
        Map<String, Object> options = new LinkedHashMap<>();
        int page = parseInt(query.get("page"), 1);
        int limit = parseInt(query.get("limit"), 20);
        options.put("page", page);
        options.put("limit", limit);
        options.put("skip", (page - 1) * limit);
        putIfPresent(options, "complainType", query.get("complainType"));
        putIfPresent(options, "status", query.get("status"));
        putIfPresent(options, "search", query.get("search"));
        JsonNode resp = http.get(helperdeskUrl + "/get-support-tickets-by-admin", options);
        if (!statusOk(resp)) {
            return fail(StatusCode.NOT_FOUND, msg(resp, "Failed to fetch support tickets"), emptyArray());
        }
        return success(StatusCode.SUCCESS, msg(resp, "Support tickets fetched successfully"), dataOr(resp, emptyArray()));
    }

    public ResponseEntity<?> closeSupport(String ticketId, String closingMessage) {
        Map<String, Object> params = new LinkedHashMap<>();
        putIfPresent(params, "ticketId", ticketId);
        putIfPresent(params, "closingMessage", closingMessage);
        JsonNode resp = http.delete(helperdeskUrl + "/close-support-ticket", params);
        if (!statusOk(resp)) {
            return fail(StatusCode.NOT_FOUND, msg(resp, "Failed to close support ticket"), emptyArray());
        }
        return success(StatusCode.SUCCESS, msg(resp, "Support ticket closed successfully"), dataOr(resp, emptyObject()));
    }

    public ResponseEntity<?> reopenSupport(String ticketId) {
        Map<String, Object> params = new LinkedHashMap<>();
        putIfPresent(params, "ticketId", ticketId);
        JsonNode resp = http.patch(helperdeskUrl + "/reopen-support-ticket", params);
        if (!statusOk(resp)) {
            return fail(StatusCode.NOT_FOUND, msg(resp, "Failed to reopen support ticket"), emptyArray());
        }
        return success(StatusCode.SUCCESS, msg(resp, "Support ticket reopened successfully"), dataOr(resp, emptyObject()));
    }

    public ResponseEntity<?> getChats(String ticketId) {
        JsonNode resp = http.get(socketUrl + "/get-chat-by-ticketid/" + ticketId);
        if (!statusOk(resp)) {
            return fail(StatusCode.NOT_FOUND, msg(resp, "Failed to fetch chat messages"), emptyArray());
        }
        return success(StatusCode.SUCCESS, msg(resp, "Chat messages fetched successfully"), dataOr(resp, emptyObject()));
    }

    public ResponseEntity<?> addChat(String msg, String from, String userId, String ticketId, MultipartFile file) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("message", msg == null ? "" : msg);
        form.add("sender", from == null ? "" : from);
        form.add("userId", userId == null ? "" : userId);
        form.add("supportTicketId", ticketId == null ? "" : ticketId);
        if (file != null && !file.isEmpty()) {
            form.add("supportImage", toResource(file));
        }
        JsonNode resp = http.putMultipart(helperdeskUrl + "/add-message", form);
        if (!statusOk(resp)) {
            return fail(StatusCode.NOT_FOUND, msg(resp, "Failed to add chat messages"), emptyArray());
        }
        return success(StatusCode.SUCCESS, msg(resp, "Chat messages fetched successfully"), dataOr(resp, emptyObject()));
    }

    // ------------------------------------------------------------------ ride / user / driver reports

    public ResponseEntity<?> getRideSummary(JsonNode body) {
        String from = body.path("from").asText(null);
        String to = body.path("to").asText(null);
        ObjectNode filters = emptyObject();
        if (from != null && !from.isEmpty() && to != null && !to.isEmpty()) {
            filters.put("fromDate", from + "T00:00:00.000Z");
            filters.put("toDate", to + "T23:59:59.999Z");
        }
        if (body.hasNonNull("rideType")) {
            filters.set("rideType", body.get("rideType"));
        }
        if (body.hasNonNull("status")) {
            filters.set("status", body.get("status"));
        }
        JsonNode resp = http.post(rideUrl + "/rides-summary", filters);
        if (!statusOk(resp)) {
            return fail(StatusCode.BAD_REQUEST, msg(resp, "Failed to fetch ride summary"), Map.of());
        }
        return success(StatusCode.SUCCESS, "Ride summary fetched successfully", dataOr(resp, emptyArray()));
    }

    public ResponseEntity<?> getUsersReport() {
        JsonNode resp = http.get(userUrl + "/get-users-report");
        if (!statusOk(resp)) {
            // NOTE: original responds status=false but success="success" / HTTP 200, echoing the whole body as data.
            return api.send(false, StatusResult.SUCCESS, StatusCode.SUCCESS,
                    msg(resp, "User report failed to fetch"), resp);
        }
        return success(StatusCode.SUCCESS, "User report fetched successfully", resp);
    }

    public ResponseEntity<?> getUsersReportList(JsonNode body) {
        int page = body.path("page").asInt(1);
        int limit = body.path("limit").asInt(10);
        String searchText = body.path("searchText").asText("").trim();
        ObjectNode payload = emptyObject();
        payload.put("page", page);
        payload.put("limit", limit);
        if (!searchText.isEmpty()) {
            payload.put("searchText", searchText);
        }
        JsonNode resp = http.post(userUrl + "/get-users-ride-report", payload);
        if (!statusOk(resp)) {
            return fail(StatusCode.BAD_REQUEST, msg(resp, "User report failed to fetch"), dataOr(resp, emptyArray()));
        }
        return success(StatusCode.SUCCESS, "User report fetched successfully", dataOr(resp, emptyObject()));
    }

    public ResponseEntity<?> getUserRideReport(String userId, Map<String, String> params) {
        if (userId == null || userId.isEmpty()) {
            return fail(StatusCode.BAD_REQUEST, "User id is required", Map.of());
        }
        Map<String, Object> q = new LinkedHashMap<>();
        putIfPresent(q, "page", params.get("page"));
        putIfPresent(q, "limit", params.get("limit"));
        putIfPresent(q, "search", params.get("search"));
        putIfPresent(q, "type", params.get("type"));
        JsonNode resp = http.get(rideUrl + "/user-ride-summary/" + userId, q);
        if (!statusOk(resp)) {
            return fail(StatusCode.BAD_REQUEST, msg(resp, "User ride report failed to fetch"), dataOr(resp, emptyArray()));
        }
        return success(StatusCode.SUCCESS, "User ride report fetched successfully", dataOr(resp, emptyObject()));
    }

    public ResponseEntity<?> updateUser(JsonNode body) {
        ObjectNode payload = emptyObject();
        payload.set("userid", body.get("userId"));
        payload.set("isBlocked", body.get("isBlocked"));
        JsonNode resp = http.post(userUrl + "/update-profile", payload);
        return success(StatusCode.SUCCESS, "User details updated successfully", resp);
    }

    public ResponseEntity<?> getDriversReport() {
        JsonNode resp = http.get(driverUrl + "/get-drivers-report");
        if (!statusOk(resp)) {
            return api.send(false, StatusResult.SUCCESS, StatusCode.SUCCESS,
                    msg(resp, "Drivers report failed to fetch"), resp);
        }
        return success(StatusCode.SUCCESS, "Drivers report fetched successfully", resp);
    }

    public ResponseEntity<?> updateDriver(JsonNode body) {
        ObjectNode payload = emptyObject();
        payload.set("driverId", body.get("driverId"));
        payload.set("isBlocked", body.get("isBlocked"));
        JsonNode resp = http.post(driverUrl + "/update-profile", payload);
        return success(StatusCode.SUCCESS, "Driver details updated successfully", resp);
    }

    public ResponseEntity<?> getDriverDetails(String driverId) {
        if (driverId == null || driverId.isEmpty()) {
            return fail(StatusCode.BAD_REQUEST, "Driver id is required", Map.of());
        }
        JsonNode resp = http.get(driverUrl + "/driver-details/" + driverId);
        if (!statusOk(resp)) {
            return fail(StatusCode.NOT_FOUND, msg(resp, "Driver details failed to fetch"), dataOr(resp, emptyObject()));
        }
        return success(StatusCode.SUCCESS, "Driver details fetched successfully", dataOr(resp, emptyObject()));
    }

    public ResponseEntity<?> getDriversReportList(JsonNode body) {
        int page = body.path("page").asInt(1);
        int limit = body.path("limit").asInt(10);
        String searchText = body.path("searchText").asText("").trim();

        ObjectNode payload = emptyObject();
        payload.put("page", page);
        payload.put("limit", limit);
        if (body.hasNonNull("search")) {
            payload.set("searchText", body.get("search"));
        }
        if (body.hasNonNull("kycStatus")) {
            payload.set("kycStatus", body.get("kycStatus"));
        }
        if (body.hasNonNull("driverStatus")) {
            payload.set("driverStatus", body.get("driverStatus"));
        }
        if (body.hasNonNull("sort")) {
            payload.set("sort", body.get("sort"));
        }
        if (body.hasNonNull("genderFilter")) {
            payload.set("genderFilter", body.get("genderFilter"));
        }
        payload.set("blockFilter", body.hasNonNull("blockFilter") ? body.get("blockFilter") : null);
        if (!searchText.isEmpty()) {
            payload.put("searchText", searchText);
        }

        JsonNode driverData = http.post(driverUrl + "/get-drivers-ride-report", payload);
        if (!statusOk(driverData)) {
            return fail(StatusCode.BAD_REQUEST, msg(driverData, "Driver report failed to fetch"),
                    dataOr(driverData, emptyArray()));
        }

        JsonNode list = driverData.path("data").path("list");
        ArrayNode enriched = mapper.createArrayNode();
        if (list.isArray()) {
            for (JsonNode driver : list) {
                ObjectNode row = driver.deepCopy();
                ObjectNode summaryReq = emptyObject();
                summaryReq.set("driverId", driver.get("_id"));
                summaryReq.put("summaryOnly", true);
                JsonNode summaryResp = http.post(rideUrl + "/driver-ride-summary/" + driver.path("_id").asText(""), summaryReq);
                JsonNode summary = summaryResp.path("data").path("summary");
                if (statusOk(summaryResp) && summary.isArray() && summary.size() > 0) {
                    row.set("rideSummary", summary);
                } else {
                    row.set("rideSummary", defaultRideSummary());
                }
                enriched.add(row);
            }
        }
        ObjectNode responseData = driverData.path("data").isObject()
                ? ((ObjectNode) driverData.get("data")).deepCopy()
                : emptyObject();
        responseData.set("list", enriched);
        return success(StatusCode.SUCCESS, "Driver report fetched successfully", responseData);
    }

    private ArrayNode defaultRideSummary() {
        ArrayNode arr = mapper.createArrayNode();
        ObjectNode o = emptyObject();
        ObjectNode rides = emptyObject();
        rides.put("total", 0);
        rides.put("completed", 0);
        rides.put("cancelled", 0);
        rides.put("ongoing", 0);
        o.set("rides", rides);
        ObjectNode distance = emptyObject();
        distance.put("totalKm", 0);
        o.set("distance", distance);
        ObjectNode duration = emptyObject();
        duration.put("totalSeconds", 0);
        o.set("duration", duration);
        ObjectNode revenue = emptyObject();
        revenue.put("total", 0);
        revenue.put("driverEarnings", 0);
        o.set("revenue", revenue);
        arr.add(o);
        return arr;
    }

    public ResponseEntity<?> getDriversRideReport(String driverId, JsonNode body) {
        if (driverId == null || driverId.isEmpty()) {
            return fail(StatusCode.BAD_REQUEST, "Driver id is required", Map.of());
        }
        int page = Math.max(body.path("page").asInt(1), 1);
        int limit = Math.max(body.path("limit").asInt(10), 1);
        int skip = (page - 1) * limit;
        ObjectNode payload = emptyObject();
        payload.put("driverId", driverId);
        payload.put("skip", skip);
        payload.put("limit", limit);
        if (body.hasNonNull("search")) {
            payload.set("search", body.get("search"));
        }
        JsonNode resp = http.post(rideUrl + "/driver-ride-summary/" + driverId, payload);
        if (!statusOk(resp)) {
            return fail(StatusCode.BAD_REQUEST, msg(resp, "Driver ride report failed to fetch"),
                    dataOr(resp, emptyArray()));
        }
        return success(StatusCode.SUCCESS, "Driver ride report fetched successfully", dataOr(resp, emptyObject()));
    }

    // ------------------------------------------------------------------ driver wallet claims

    public ResponseEntity<?> getDriverWalletClaimRequests(JsonNode body) {
        requireAdmin("Only admins can view claim requests");
        JsonNode resp = http.post(driverUrl + "/claim-wallet-list", body);
        if (!statusOk(resp)) {
            return fail(StatusCode.BAD_REQUEST, msg(resp, "Failed to fetch driver wallet claims"), dataOr(resp, resp));
        }
        return success(StatusCode.SUCCESS, msg(resp, "Driver wallet claims fetched successfully"), dataOr(resp, resp));
    }

    public ResponseEntity<?> reviewDriverWalletClaimRequest(JsonNode body) {
        requireAdmin("Only admins can review claim requests");
        JsonNode resp = http.post(driverUrl + "/claim-wallet-review", body);
        if (!statusOk(resp)) {
            return fail(StatusCode.BAD_REQUEST, msg(resp, "Failed to review driver wallet claim"), dataOr(resp, resp));
        }
        return success(StatusCode.SUCCESS, msg(resp, "Driver wallet claim reviewed successfully"), dataOr(resp, resp));
    }

    public ResponseEntity<?> payDriverWalletClaimRequest(JsonNode body) {
        requireAdmin("Only admins can pay claim requests");
        JsonNode resp = http.post(driverUrl + "/claim-wallet-pay", body);
        if (!statusOk(resp)) {
            return fail(StatusCode.BAD_REQUEST, msg(resp, "Failed to pay driver wallet claim"), dataOr(resp, resp));
        }
        return success(StatusCode.SUCCESS, msg(resp, "Driver wallet claim paid successfully"), dataOr(resp, resp));
    }

    // ------------------------------------------------------------------ KYC

    public ResponseEntity<?> getDriversKycList(JsonNode body) {
        int page = body.path("page").asInt(1);
        int limit = body.path("limit").asInt(10);
        String searchText = body.path("searchText").asText("").trim();
        String kycStatus = body.path("kycStatus").asText("pending");
        ObjectNode payload = emptyObject();
        payload.put("page", page);
        payload.put("limit", limit);
        payload.put("kycStatus", kycStatus);
        if (!searchText.isEmpty()) {
            payload.put("searchText", searchText);
        }
        JsonNode resp = http.post(driverUrl + "/drivers-kyc-list", payload);
        if (!statusOk(resp)) {
            return fail(StatusCode.BAD_REQUEST, msg(resp, "Failed to fetch KYC list"), emptyArray());
        }
        return success(StatusCode.SUCCESS, "KYC list fetched successfully", dataOr(resp, emptyObject()));
    }

    public ResponseEntity<?> verifyDriverDocument(String driverId, String key, JsonNode body) {
        String documentStatus = body.path("documentStatus").asText(null);
        JsonNode driver = http.get(driverUrl + "/driver-details/" + driverId);
        if (!statusOk(driver)) {
            return fail(StatusCode.BAD_REQUEST, "Driver not found", Map.of());
        }
        JsonNode driverData = driver.path("data");
        ObjectNode payload = emptyObject();
        payload.put("_id", driverId);
        payload.put("idtype", key);
        payload.put("verifyStatus", documentStatus);
        payload.put("email", driverData.path("email").asText(null));
        JsonNode result = http.post(driverUrl + "/kycverify", payload);
        if (result != null && !result.isMissingNode()) {
            return success(StatusCode.SUCCESS, "Document " + documentStatus + " successfully", null);
        }
        // Original returns nothing (request hangs) when result is falsy; surface an error instead.
        return fail(StatusCode.BAD_REQUEST, "Failed to update document status", Map.of());
    }

    // ------------------------------------------------------------------ users list / rides

    public ResponseEntity<?> getUsersList(Map<String, String> query) {
        int page = parseInt(query.get("page"), 1);
        int limit = parseInt(query.get("limit"), 10);
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("skip", (page - 1) * limit);
        options.put("limit", limit);
        putIfPresent(options, "fromDate", query.get("fromDate"));
        putIfPresent(options, "toDate", query.get("toDate"));
        ObjectNode payload = emptyObject();
        payload.set("options", mapper.valueToTree(options));
        putIfPresent(payload, "search", query.get("search"));
        JsonNode resp = http.post(userUrl + "/get-users-list", payload);
        if (!statusOk(resp)) {
            return fail(StatusCode.NOT_FOUND, msg(resp, "Failed to fetch users list"), emptyArray());
        }
        return success(StatusCode.SUCCESS, msg(resp, "Users fetched successfully"), dataOr(resp, emptyArray()));
    }

    public ResponseEntity<?> getAllRides(Map<String, String> query) {
        Map<String, Object> params = new LinkedHashMap<>();
        putIfPresent(params, "page", query.get("page"));
        putIfPresent(params, "limit", query.get("limit"));
        putIfPresent(params, "search", query.get("search"));
        putIfPresent(params, "fromDate", query.get("fromDate"));
        putIfPresent(params, "toDate", query.get("toDate"));
        putIfPresent(params, "rideType", query.get("rideType"));
        JsonNode resp = http.get(rideUrl + "/get-all-rides", params);
        return success(StatusCode.SUCCESS, msg(resp, "Rides fetched successfully"), dataOr(resp, emptyArray()));
    }

    public ResponseEntity<?> getOutstationRide(JsonNode body) {
        ObjectNode options = emptyObject();
        options.set("userId", body.get("userId"));
        options.set("driverId", body.get("driverId"));
        options.set("status", body.get("status"));
        options.put("page", body.path("page").asInt(1));
        options.put("limit", body.path("limit").asInt(10));
        options.set("search", body.get("search"));
        options.put("onTripRoute", body.path("onTripRoute").asBoolean(false));
        JsonNode resp = http.post(rideUrl + "/outstation-ride-list", options);
        if (!statusOk(resp)) {
            return fail(StatusCode.NOT_FOUND, msg(resp, "Failed to fetch users list"), emptyArray());
        }
        return success(StatusCode.SUCCESS, msg(resp, "Users fetched successfully"), dataOr(resp, emptyArray()));
    }

    // ------------------------------------------------------------------ track cabs

    public ResponseEntity<?> getOngoingRidesAdmin(Map<String, String> query) {
        Map<String, Object> params = new LinkedHashMap<>();
        putIfPresent(params, "rideType", query.get("rideType"));
        putIfPresent(params, "rideId", query.get("rideId"));
        putIfPresent(params, "page", query.get("page"));
        putIfPresent(params, "limit", query.get("limit"));
        params.put("includeGPS", query.get("includeGPS") != null && !query.get("includeGPS").isEmpty());
        params.put("includeProfiles", query.get("includeProfiles") != null && !query.get("includeProfiles").isEmpty());
        putIfPresent(params, "sort", query.get("sort"));
        JsonNode resp = http.get(rideUrl + "/ongoing-rides", params);
        if (!statusOk(resp)) {
            return fail(code(resp, StatusCode.BAD_REQUEST), msg(resp, "Failed to fetch ongoing rides"),
                    dataOr(resp, emptyArray()));
        }
        return success(StatusCode.SUCCESS, msg(resp, "Ongoing rides fetched successfully"), dataOr(resp, emptyObject()));
    }

    public ResponseEntity<?> getOngoingRideGps(String rideId, String limit) {
        if (rideId == null || rideId.isEmpty()) {
            return fail(StatusCode.BAD_REQUEST, "Ride ID is required", emptyArray());
        }
        Map<String, Object> params = new LinkedHashMap<>();
        putIfPresent(params, "limit", limit);
        JsonNode resp = http.get(rideUrl + "/ongoing-rides/" + rideId + "/gps", params);
        if (!statusOk(resp)) {
            return fail(code(resp, StatusCode.BAD_REQUEST), msg(resp, "Failed to fetch ride GPS"),
                    dataOr(resp, emptyArray()));
        }
        return success(StatusCode.SUCCESS, msg(resp, "Ride GPS fetched successfully"), dataOr(resp, emptyObject()));
    }

    public ResponseEntity<?> getRideActivityReport(String from, String to) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("from", from == null ? "" : from);
        params.put("to", to == null ? "" : to);
        JsonNode resp = http.get(rideUrl + "/ride-activity", params);
        if (!statusOk(resp)) {
            return fail(code(resp, StatusCode.BAD_REQUEST), msg(resp, "Failed to fetch ride activity"),
                    dataOr(resp, emptyArray()));
        }
        return success(StatusCode.SUCCESS, msg(resp, "Ride activity fetched successfully"), dataOr(resp, emptyObject()));
    }

    // ------------------------------------------------------------------ driver fetch config

    public ResponseEntity<?> getDriverFetchConfig() {
        JsonNode resp = http.get(rideUrl + "/get-driver-fetch-config");
        if (!statusOk(resp)) {
            return fail(StatusCode.NOT_FOUND, msg(resp, "Failed to fetch driver fetch config"), emptyArray());
        }
        return success(StatusCode.SUCCESS, msg(resp, "Driver fetch config fetched successfully"), dataOr(resp, emptyObject()));
    }

    public ResponseEntity<?> updateDriverFetchConfig(JsonNode body) {
        ObjectNode options = emptyObject();
        options.set("driverFetchConfigId", body.get("driverFetchConfigId"));
        options.set("withinKm", body.get("withinKm"));
        options.set("driverCount", body.get("driverCount"));
        options.set("requestPerDriver", body.get("requestPerDriver"));
        options.set("kmRestriction", body.get("kmRestriction"));
        JsonNode resp = http.post(rideUrl + "/update-driver-fetch-config", options);
        if (!statusOk(resp)) {
            return fail(StatusCode.BAD_REQUEST, msg(resp, "Failed to update driver fetch config"), emptyArray());
        }
        return success(StatusCode.SUCCESS, msg(resp, "Driver fetch config updated successfully"), dataOr(resp, emptyObject()));
    }

    // ------------------------------------------------------------------ vehicle type / variant / price

    public ResponseEntity<?> vehicleVariantList(Map<String, String> query) {
        int page = parseInt(query.get("page"), 0);
        int limit = parseInt(query.get("limit"), 0);
        int skip = page > 0 && limit > 0 ? (page - 1) * limit : 0;
        Map<String, Object> params = new LinkedHashMap<>();
        putIfPresent(params, "page", query.get("page"));
        params.put("skip", skip);
        putIfPresent(params, "limit", query.get("limit"));
        putIfPresent(params, "search", query.get("search"));
        putIfPresent(params, "vehicleTypeId", query.get("vehicleTypeId"));
        if ("true".equals(query.get("isLocal"))) {
            params.put("isLocal", true);
        } else if ("true".equals(query.get("isOutstation"))) {
            params.put("isOutstation", true);
        }
        JsonNode resp = http.get(vehicleUrl + "/vehicle-variant-list", params);
        if (!statusOk(resp)) {
            return fail(StatusCode.NOT_FOUND, msg(resp, "Failed to fetch vehicle variant list"), emptyArray());
        }
        return success(StatusCode.SUCCESS, msg(resp, "Vehicle variants fetched successfully"), dataOr(resp, emptyArray()));
    }

    public ResponseEntity<?> vehicleTypeList(Map<String, String> query) {
        int page = parseInt(query.get("page"), 0);
        int limit = parseInt(query.get("limit"), 0);
        Map<String, Object> params = new LinkedHashMap<>();
        putIfPresent(params, "page", query.get("page"));
        params.put("skip", (page - 1) * limit);
        putIfPresent(params, "limit", query.get("limit"));
        putIfPresent(params, "search", query.get("search"));
        JsonNode resp = http.get(vehicleUrl + "/vehicle-type-list", params);
        if (!statusOk(resp)) {
            return fail(StatusCode.NOT_FOUND, msg(resp, "Failed to fetch vehicle type list"), emptyArray());
        }
        return success(StatusCode.SUCCESS, msg(resp, "Vehicle type fetched successfully"), dataOr(resp, emptyArray()));
    }

    public ResponseEntity<?> addVehicleVariant(Map<String, String> fields, MultipartFile imageFile) {
        return forwardVehicleMultipart(vehicleUrl + "/add-vehicle-variant", fields, imageFile);
    }

    public ResponseEntity<?> updateVehicleVariant(Map<String, String> fields, MultipartFile imageFile) {
        return forwardVehicleMultipart(vehicleUrl + "/update-vehicle-variant", fields, imageFile);
    }

    public ResponseEntity<?> addVehicleType(Map<String, String> fields, MultipartFile imageFile) {
        return forwardVehicleMultipart(vehicleUrl + "/add-vehicle-type", fields, imageFile);
    }

    public ResponseEntity<?> updateVehicleType(Map<String, String> fields, MultipartFile imageFile) {
        return forwardVehicleMultipart(vehicleUrl + "/update-vehicle-type", fields, imageFile);
    }

    private ResponseEntity<?> forwardVehicleMultipart(String url, Map<String, String> fields, MultipartFile imageFile) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        if (fields != null) {
            for (Map.Entry<String, String> e : fields.entrySet()) {
                if (e.getValue() != null) {
                    form.add(e.getKey(), e.getValue());
                }
            }
        }
        if (imageFile != null && !imageFile.isEmpty()) {
            form.add("imageFile", toResource(imageFile));
        }
        JsonNode resp = http.postMultipart(url, form);
        if (!statusOk(resp)) {
            return fail(StatusCode.NOT_FOUND, msg(resp, "Failed to add vehicle variant list"), emptyArray());
        }
        return success(StatusCode.SUCCESS, msg(resp, "Vehicle variants added successfully"), dataOr(resp, emptyArray()));
    }

    public ResponseEntity<?> addPrice(JsonNode body) {
        JsonNode resp = http.post(vehicleUrl + "/add-price", body);
        if (!statusOk(resp)) {
            return fail(StatusCode.NOT_FOUND, msg(resp, "Failed to add price"), emptyArray());
        }
        return success(StatusCode.SUCCESS, msg(resp, "Price added successfully"), dataOr(resp, emptyObject()));
    }

    public ResponseEntity<?> updatePrice(JsonNode body) {
        JsonNode resp = http.post(vehicleUrl + "/update-price", body);
        if (!statusOk(resp)) {
            return fail(StatusCode.NOT_FOUND, msg(resp, "Failed to add price"), emptyArray());
        }
        return success(StatusCode.SUCCESS, msg(resp, "Price added successfully"), dataOr(resp, emptyObject()));
    }

    // ------------------------------------------------------------------ export

    public ExportPayload export(Map<String, String> query) {
        String report = query.get("report");
        String type = query.get("type");
        if (report == null || report.isEmpty()) {
            throw new ApiException(StatusCode.NOT_FOUND, "No reports created");
        }
        if (type == null || type.isEmpty()) {
            throw new ApiException(StatusCode.NOT_FOUND, "Report type is invalid");
        }
        Map<String, Object> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : query.entrySet()) {
            if (!"report".equals(e.getKey()) && !"type".equals(e.getKey())) {
                putIfPresent(filters, e.getKey(), e.getValue());
            }
        }
        Map<String, Object> withType = new LinkedHashMap<>(filters);
        withType.put("type", type);

        Map<String, String> contentTypes = Map.of(
                "pdf", "application/pdf",
                "csv", "text/csv",
                "excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        switch (report) {
            case "invoice": {
                String rideId = (String) filters.get("rideId");
                if (rideId == null || rideId.isEmpty()) {
                    throw new ApiException(StatusCode.BAD_REQUEST, "rideId is required");
                }
                byte[] bytes = http.getBytes(rideUrl + "/rides/" + rideId + "/invoice", Map.of());
                if (bytes == null) {
                    throw new ApiException(StatusCode.NOT_FOUND, "Failed to export ride invoice");
                }
                return new ExportPayload(bytes, "application/pdf", "invoice_" + rideId + ".pdf");
            }
            case "user":
                return exportEntity(userUrl + "/export", withType, type, contentTypes, "users",
                        "Failed to export user report");
            case "ride":
                return exportEntity(rideUrl + "/export", withType, type, contentTypes, "rides",
                        "Failed to export ride report");
            case "driver":
                return exportEntity(driverUrl + "/export", withType, type, contentTypes, "drivers",
                        "Failed to export driver report");
            case "vehicleType":
                return exportEntity(vehicleUrl + "/export", withType, type, contentTypes, "vehicle_types",
                        "Failed to export vehicle type report");
            case "vehicleVariant":
                return exportEntity(vehicleUrl + "/variant-export", withType, type, contentTypes, "vehicle_variants",
                        "Failed to export vehicle variant report");
            case "coupon":
                // couponExport lives in the coupon bucket's controller scope; not reachable from here.
                throw new ApiException(StatusCode.INTERNAL_SERVER_ERROR, "couponExport is not defined");
            default:
                throw new ApiException(StatusCode.NOT_FOUND, "Invalid report type");
        }
    }

    private ExportPayload exportEntity(String url, Map<String, Object> params, String type,
            Map<String, String> contentTypes, String base, String failMsg) {
        byte[] bytes = http.getBytes(url, params);
        if (bytes == null) {
            throw new ApiException(StatusCode.NOT_FOUND, failMsg);
        }
        String ext = "excel".equals(type) ? "xlsx" : type;
        String contentType = contentTypes.getOrDefault(type, "application/octet-stream");
        return new ExportPayload(bytes, contentType, base + "." + ext);
    }

    public record ExportPayload(byte[] body, String contentType, String filename) {
    }

    // ------------------------------------------------------------------ small utils

    private static int parseInt(String v, int def) {
        if (v == null || v.isEmpty()) {
            return def;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isEmpty()) {
            map.put(key, value);
        }
    }

    private void putIfPresent(ObjectNode node, String key, String value) {
        if (value != null && !value.isEmpty()) {
            node.put(key, value);
        }
    }

    private static org.springframework.core.io.Resource toResource(MultipartFile file) {
        try {
            return new org.springframework.core.io.ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
        } catch (Exception e) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Invalid file upload");
        }
    }
}
