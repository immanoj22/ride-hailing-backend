package com.ridehail.rideservice.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.constants.StatusResult;
import com.ridehail.common.response.ApiResponseService;
import com.ridehail.common.security.AuthContext;
import com.ridehail.rideservice.service.RideSummaryService;

/**
 * Port of the ratings-and-ride-summaries read endpoints in ride.controller.js
 * (routes/ride.routes.js, mounted at /ride-apis in server.js).
 */
@RestController
@RequestMapping("/ride-apis")
public class RideSummaryController {

    private final RideSummaryService service;
    private final ApiResponseService apiResponseService;

    public RideSummaryController(RideSummaryService service, ApiResponseService apiResponseService) {
        this.service = service;
        this.apiResponseService = apiResponseService;
    }

    @PostMapping("/rides-summary")
    public ResponseEntity<?> getRidesSummary(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> b = body == null ? Map.of() : body;
        Map<String, Object> data = service.getRidesSummary(
                str(b.get("fromDate")), str(b.get("toDate")), str(b.get("rideType")), str(b.get("status")));
        return apiResponseService.send(true, StatusResult.SUCCESS, StatusCode.SUCCESS,
                "Dashboard summary fetched successfully", data);
    }

    @GetMapping("/user-ride-summary/{userId}")
    public ResponseEntity<?> getUserRideSummary(@PathVariable String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "both") String type) {
        Map<String, Object> data = service.getUserRideSummary(userId, page, limit, search);
        return apiResponseService.send(true, StatusResult.SUCCESS, StatusCode.SUCCESS,
                "User ride summary fetched successfully", data);
    }

    @PostMapping("/driver-ride-summary/{driverId}")
    public ResponseEntity<?> getDriverRideSummary(@PathVariable(required = false) String driverId,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> b = body == null ? Map.of() : body;
        String effectiveDriverId = driverId != null ? driverId : str(b.get("driverId"));
        boolean summaryOnly = Boolean.TRUE.equals(b.get("summaryOnly"));
        int page = intOr(b.get("page"), 1);
        int limit = intOr(b.get("limit"), 10);
        Map<String, Object> data = service.getDriverRideSummary(effectiveDriverId, summaryOnly, page, limit,
                str(b.get("search")));
        return apiResponseService.send(true, StatusResult.SUCCESS, StatusCode.SUCCESS,
                "Driver ride summary fetched successfully", data);
    }

    @PostMapping("/driver/{driverId}/rides")
    public ResponseEntity<?> getDriverRides(@PathVariable String driverId,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> b = body == null ? Map.of() : body;
        String status = b.get("status") != null ? str(b.get("status")) : "active";
        List<Map<String, Object>> rides = service.getDriverRides(driverId, status);
        return apiResponseService.send(true, StatusResult.SUCCESS, StatusCode.SUCCESS,
                "Driver rides fetched successfully", rides);
    }

    @GetMapping("/active/driver/{driverId}")
    public ResponseEntity<?> getActiveRideByDriver(@PathVariable String driverId) {
        Map<String, Object> data = service.getActiveRideByDriver(driverId);
        if (data == null) {
            return apiResponseService.send(true, StatusResult.SUCCESS, StatusCode.SUCCESS, "No active ride", null);
        }
        return apiResponseService.send(true, StatusResult.SUCCESS, StatusCode.SUCCESS,
                "Active ride fetched successfully", data);
    }

    @GetMapping("/pending/driver/{driverId}")
    public ResponseEntity<?> getPendingRideByDriver(@PathVariable String driverId) {
        Map<String, Object> data = service.getPendingRideByDriver(driverId);
        if (data == null) {
            return apiResponseService.send(true, StatusResult.SUCCESS, StatusCode.SUCCESS, "No pending ride", null);
        }
        return apiResponseService.send(true, StatusResult.SUCCESS, StatusCode.SUCCESS, "Pending ride found", data);
    }

    @GetMapping("/get-latest-ride-user/{userId}")
    public ResponseEntity<?> getLatestRideByUser(@PathVariable String userId) {
        Map<String, Object> data = service.getLatestRideByUser(userId);
        if (data == null) {
            return apiResponseService.send(false, StatusResult.SUCCESS, StatusCode.NO_CONTENT,
                    "No rides found for user");
        }
        return apiResponseService.send(true, StatusResult.SUCCESS, StatusCode.SUCCESS,
                "Latest ride fetched successfully", data);
    }

    @GetMapping("/get-rides-by-user")
    public ResponseEntity<?> getRidesByUser(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String active) {
        String userId = AuthContext.current() != null ? AuthContext.current().principalId() : null;
        List<Map<String, Object>> rides = service.getRidesByUser(userId, page, limit, active);
        if (rides.isEmpty()) {
            return apiResponseService.send(false, StatusResult.SUCCESS, StatusCode.BAD_REQUEST,
                    "No rides found for user");
        }
        return apiResponseService.send(true, StatusResult.SUCCESS, StatusCode.SUCCESS,
                "Rides fetched successfully", rides);
    }

    @GetMapping("/getUsersRideList")
    public ResponseEntity<?> getUsersRideList(@RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String userId) {
        JsonNode resp = service.getUsersRideList(page, limit, search);
        boolean ok = resp != null && resp.path("status").asBoolean(false);
        if (!ok) {
            String msg = resp != null && resp.hasNonNull("message")
                    ? resp.get("message").asText() : "Failed to fetch users list";
            return apiResponseService.send(false, StatusResult.ERROR, StatusCode.NOT_FOUND, msg, List.of());
        }
        String msg = resp.hasNonNull("message") ? resp.get("message").asText() : "Users fetched successfully";
        return apiResponseService.send(true, StatusResult.SUCCESS, StatusCode.SUCCESS, msg, resp.get("data"));
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static int intOr(Object o, int def) {
        if (o instanceof Number n) {
            return n.intValue() > 0 ? n.intValue() : def;
        }
        if (o != null) {
            try {
                int v = Integer.parseInt(String.valueOf(o));
                return v > 0 ? v : def;
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return def;
    }
}
