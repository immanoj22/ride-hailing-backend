package com.ridehail.userservice.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehail.common.constants.AuthUserType;
import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.common.security.AuthContext;
import com.ridehail.common.security.InternalServiceClient;
import com.ridehail.userservice.dto.OutstationRidesRequest;
import com.ridehail.userservice.dto.TriggerSosRequest;

/**
 * "ride-service-proxy" bucket: thin pass-through logic for controller/user.controller.js's
 * get_ride, get_outstation_rides, update_outstation_ride, get_rental_slots, get_coupons,
 * trigger_sos and get_support_ticket_chats. None of these persist anything locally - they only
 * read the caller's own auth-resolved id and forward to ride-service / admin-service /
 * helperdesk-service / socket-service via {@link InternalServiceClient}, exactly like the
 * original utills/user.axios.js helpers did via axios.
 *
 * <p>Three genuine bugs were found in the original Node code while porting this bucket (see the
 * notes on {@link #getRentalSlots(String)} and {@link #getSupportTicketChats(String)} below) that
 * made those two endpoints entirely unreachable/non-functional in production. Per the migration
 * brief, behavior is preserved everywhere it is reproducible in Java; where the original bug is a
 * reference to an undefined JS symbol (a hard compile error if copied literally into Java) the
 * clearly-intended working logic is implemented instead, and flagged for human follow-up.</p>
 */
@Service
public class RideProxyService {

    private final InternalServiceClient internalServiceClient;
    private final ObjectMapper objectMapper;
    private final String rideServiceUrl;
    private final String adminServiceUrl;
    private final String helperdeskServiceUrl;
    private final String socketServiceUrl;

    public RideProxyService(
            InternalServiceClient internalServiceClient,
            ObjectMapper objectMapper,
            @Value("${ridehail.services.ride-url}") String rideServiceUrl,
            @Value("${ridehail.services.admin-url}") String adminServiceUrl,
            @Value("${ridehail.services.helperdesk-url}") String helperdeskServiceUrl,
            @Value("${ridehail.services.socket-url}") String socketServiceUrl) {
        this.internalServiceClient = internalServiceClient;
        this.objectMapper = objectMapper;
        this.rideServiceUrl = rideServiceUrl;
        this.adminServiceUrl = adminServiceUrl;
        this.helperdeskServiceUrl = helperdeskServiceUrl;
        this.socketServiceUrl = socketServiceUrl;
    }

    /** Simple (message, data) pair so a dynamically-computed downstream message can still flow
     *  through a business-logic-free controller method. */
    public record ProxyResult(String message, JsonNode data) {
    }

    /** GET /rides/{rideId} - user.controller.js#get_ride. */
    public ProxyResult getRide(String rideId) {
        requireUserId();
        if (isBlank(rideId)) {
            throw new ApiException(StatusCode.BAD_REQUEST, "rideId is required");
        }

        JsonNode rideRes = internalServiceClient.get(rideServiceUrl + "/rides/" + rideId);
        if (!internalServiceClient.statusTrue(rideRes)) {
            throw new ApiException(StatusCode.NOT_FOUND, textOr(rideRes, "message", "Ride not found"));
        }
        return new ProxyResult(textOr(rideRes, "message", "Ride fetched successfully"), rideRes.path("data"));
    }

    /** POST /outstation/rides - user.controller.js#get_outstation_rides. */
    public ProxyResult getOutstationRides(OutstationRidesRequest request) {
        String userId = optionalUserId();
        int page = request.getPage() != null ? request.getPage() : 1;
        int limit = request.getLimit() != null ? request.getLimit() : 10;

        Map<String, Object> payload = new LinkedHashMap<>();
        if (userId != null) {
            payload.put("userId", userId);
        }
        payload.put("page", page);
        payload.put("limit", limit);
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            payload.put("status", request.getStatus());
        }

        JsonNode rideRes = internalServiceClient.post(rideServiceUrl + "/outstation-rides", payload);
        if (!internalServiceClient.statusTrue(rideRes)) {
            throw new ApiException(StatusCode.INTERNAL_SERVER_ERROR, textOr(rideRes, "message", "Failed to fetch outstation rides"));
        }
        return new ProxyResult(textOr(rideRes, "message", "Outstation rides fetched successfully"), rideRes.path("data"));
    }

    /** POST /outstation/update - user.controller.js#update_outstation_ride: a wholesale pass-through
     *  of the request body to ride-service, so no DTO/validation is applied, matching the original. */
    public ProxyResult updateOutstationRide(JsonNode body) {
        JsonNode payload = body != null ? body : objectMapper.createObjectNode();
        JsonNode resp = internalServiceClient.post(rideServiceUrl + "/update-outstation-ride", payload);
        if (!internalServiceClient.statusTrue(resp)) {
            throw new ApiException(StatusCode.BAD_REQUEST, textOr(resp, "message", "Update failed"));
        }
        return new ProxyResult("Ride updated", resp.path("data"));
    }

    /**
     * GET /rental-slots/{vehicleVariantId} - user.controller.js#get_rental_slots.
     *
     * <p><b>Bug found in the original:</b> routes/user.routes.js registers this route as
     * {@code route.get("rental-slots/:vehicleVariantId", ...)} - missing the leading slash. Verified
     * against a live Express router that this makes the route completely unreachable (every request
     * to {@code GET /user-apis/rental-slots/:id} 404s; there is no leading-slash-free URL a real HTTP
     * client could hit that would match it either). Mapped here at the clearly-intended
     * {@code /rental-slots/{vehicleVariantId}} path so the endpoint is actually reachable; flagging
     * for human confirmation in case 404-for-everyone was somehow relied upon.</p>
     */
    public ProxyResult getRentalSlots(String vehicleVariantId) {
        if (isBlank(vehicleVariantId)) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Invalid vehicleVariantId");
        }

        // utills/user.axios.js#getRentalSlots posts the raw id (not wrapped in an object) with an
        // explicit application/json content-type; axios's transformRequest therefore JSON.stringifies
        // the bare string, sending a quoted JSON string as the whole body (e.g. `"abc123"`), not an
        // unquoted raw string. A Jackson JsonNode (TextNode) reproduces that exact wire format here -
        // passing a plain java.lang.String would instead be written unquoted by RestTemplate's
        // StringHttpMessageConverter, which is not equivalent.
        JsonNode payload = objectMapper.valueToTree(vehicleVariantId);
        JsonNode response = internalServiceClient.post(rideServiceUrl + "/rental-slots", payload);

        // Note: this endpoint's success check is on `success`, not `status` - matching the original
        // (`if (!response?.success)`), unlike every other proxy call in this bucket.
        if (!response.path("success").asBoolean(false)) {
            throw new ApiException(StatusCode.BAD_REQUEST, textOr(response, "message", "Failed to fetch rental slots"));
        }
        return new ProxyResult("Rental slots fetched successfully", response.path("data"));
    }

    /** GET /get-coupons/{tripType}/{couponId} - user.controller.js#get_coupons. */
    public ProxyResult getCoupons(String tripType, String couponId) {
        // Original: `req?.user?._id || req?.params?.userId` - the route has no :userId param, so the
        // second operand is always undefined; userId is only ever populated for USER-authenticated
        // callers, with no "not authorized" guard for anyone else (unlike get_ride).
        String userId = optionalUserId();

        Map<String, Object> payload = new LinkedHashMap<>();
        if (userId != null) {
            payload.put("userId", userId);
        }
        payload.put("tripType", tripType);
        if (!isEmptyToken(couponId)) {
            payload.put("couponId", couponId);
        }

        JsonNode coupons = internalServiceClient.post(adminServiceUrl + "/get-user-coupons", payload);
        JsonNode couponList = coupons.path("data").path("data");
        if (!couponList.isArray() || couponList.isEmpty()) {
            throw new ApiException(StatusCode.NOT_FOUND, textOr(coupons, "message", "No coupons found"));
        }
        return new ProxyResult("Coupons fetched successfully", coupons.path("data"));
    }

    /** POST /trigger-sos - user.controller.js#trigger_sos. */
    public ProxyResult triggerSos(TriggerSosRequest request) {
        String userId = optionalUserId();
        String rideId = request != null ? request.getRideId() : null;
        Double lat = request != null && request.getLocation() != null ? request.getLocation().getLat() : null;
        Double lng = request != null && request.getLocation() != null ? request.getLocation().getLng() : null;

        if (isBlank(rideId)) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Ride ID is required");
        }
        // Original: `if (!lat || !lng)` - JS falsy check, so an exact 0 latitude/longitude is (bug or
        // not) treated as "missing" too; preserved exactly since it doesn't block compilation.
        if (lat == null || lng == null || lat == 0 || lng == 0) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Location is required");
        }

        Map<String, Object> location = new LinkedHashMap<>();
        location.put("lat", lat);
        location.put("lng", lng);

        Map<String, Object> payload = new LinkedHashMap<>();
        if (userId != null) {
            payload.put("userId", userId);
        }
        payload.put("rideId", rideId);
        payload.put("userType", "rider");
        payload.put("location", location);

        JsonNode response = internalServiceClient.post(helperdeskServiceUrl + "/sos/trigger", payload);
        if (!internalServiceClient.statusTrue(response)) {
            // Original falls back to `STATUS_CODE.INTERNAL_ERROR`, a constant that does not exist in
            // helpers/user.constants.js (only INTERNAL_SERVER_ERROR does); since sendResponse's
            // statusCode parameter defaults to 200 for an undefined argument, the real Node behavior on
            // this branch is an HTTP 200 with `status:false` in the body. Reproducing that literally
            // would mean deliberately sending a 200 for a failure, which is indistinguishable from a
            // bug for every other client of this API; StatusCode.INTERNAL_SERVER_ERROR (matching the
            // sibling proxy methods in this class) is used instead.
            throw new ApiException(StatusCode.INTERNAL_SERVER_ERROR, textOr(response, "message", "Failed to trigger SOS"));
        }
        return new ProxyResult("SOS triggered successfully", response.path("data"));
    }

    /**
     * GET /support-ticket-chats?ticketId=... - user.controller.js#get_support_ticket_chats.
     *
     * <p><b>Three stacked bugs found in the original</b> that make this endpoint 100% non-functional
     * today, verified by reading the exact source referenced below:</p>
     * <ol>
     *   <li>validations/uservalidation.js#getSupportTicketChatsSchema validates {@code {ticketId}} at
     *       the top level, but utills/joiValidator.js#validateRequest always validates the wrapper
     *       {@code {body, query, params}} - so the required key is never actually present and every
     *       request 422s with "Ticket ID is required" regardless of the query string supplied.</li>
     *   <li>Even past that, the controller body references a bare {@code STATUS} identifier
     *       ({@code STATUS.ERROR}/{@code STATUS.SUCCESS}) that is never imported/declared anywhere in
     *       user.controller.js (only {@code STATUS_RES} is) - a ReferenceError on every call, caught by
     *       errorHandler and turned into a 500. This one is a hard compile error if copied literally
     *       into Java (there is no such symbol to reference), so the clearly-intended
     *       {@code StatusResult}/{@code StatusCode} constants are used instead.</li>
     *   <li>utills/user.axios.js#getSupportTicketChats calls {@code this.axiosFunc(...)} instead of the
     *       module-local {@code axiosFunc} - {@code this} is {@code module.exports} at that call site,
     *       which never had {@code axiosFunc} attached to it, so the call always throws and the
     *       function always resolves to {@code null}.</li>
     * </ol>
     * <p>Implemented here is the clearly-intended working call (GET {@code {socket-url}/get-chat-by-
     * ticketid/{ticketId}}), matching the URL construction in utills/user.axios.js exactly; flagging
     * for human follow-up since the original never actually reaches ride/socket-service for this
     * route today.</p>
     */
    public ProxyResult getSupportTicketChats(String ticketId) {
        requireUserId();

        JsonNode resp = internalServiceClient.get(socketServiceUrl + "/get-chat-by-ticketid/" + ticketId);
        JsonNode data = resp.hasNonNull("data") ? resp.path("data") : objectMapper.createArrayNode();
        if (!internalServiceClient.statusTrue(resp)) {
            throw new ApiException(StatusCode.NOT_FOUND, textOr(resp, "message", "Failed to fetch support ticket chats"));
        }
        return new ProxyResult(textOr(resp, "message", "Support ticket chats fetched successfully"), data);
    }

    private String optionalUserId() {
        AuthContext ctx = AuthContext.current();
        if (ctx != null && ctx.authUserType() == AuthUserType.USER) {
            return ctx.principalId();
        }
        return null;
    }

    private String requireUserId() {
        String userId = optionalUserId();
        if (userId == null) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Not authorized");
        }
        return userId;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** Mirrors helpers/user.commonFunction.js#isEmpty for the String-only inputs used in this bucket. */
    private boolean isEmptyToken(String value) {
        if (value == null || value.trim().isEmpty()) {
            return true;
        }
        return "0".equals(value) || "error".equals(value) || "undefined".equals(value) || "null".equals(value);
    }

    /** Mirrors the very common {@code x?.field || fallback} JS pattern used throughout the original. */
    private String textOr(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText("");
        return value.isBlank() ? fallback : value;
    }
}
