package com.ridehail.driverservice.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.common.response.ApiResponseService;
import com.ridehail.common.security.AuthContext;
import com.ridehail.driverservice.dto.DriverExportReportResult;
import com.ridehail.driverservice.dto.GetDriversRideReportRequest;
import com.ridehail.driverservice.dto.GetMyRidesRequest;
import com.ridehail.driverservice.dto.UpdateReferralRequest;
import com.ridehail.driverservice.service.DriverReportService;

import jakarta.validation.Valid;

/**
 * Direct port of driver.controller.js's get_my_rides / get_my_eranings / get_my_referral_data /
 * get_referral_earnings / get_driver_referrals_list / get_ref_driver_payment_details /
 * get_driver_dashboard_stats / get_drivers_report / get_drivers_ride_report / export_report /
 * update_referral (routes/driver.routes.js, mounted at "/driver-apis").
 *
 * <p>No business logic here (other than export_report's inherently controller-level file
 * streaming) - everything is delegated to {@link DriverReportService}.
 */
@RestController
@RequestMapping("/driver-apis")
public class DriverReportController {

    private final DriverReportService driverReportService;
    private final ApiResponseService apiResponseService;

    public DriverReportController(DriverReportService driverReportService, ApiResponseService apiResponseService) {
        this.driverReportService = driverReportService;
        this.apiResponseService = apiResponseService;
    }

    @PostMapping("/my-rides")
    public ResponseEntity<?> getMyRides(@RequestBody(required = false) GetMyRidesRequest request) {
        UUID driverId = requireAuthDriverId();
        String status = request != null ? request.getStatus() : "active";
        JsonNode data = driverReportService.getMyRides(driverId, status);
        return apiResponseService.ok("Rides fetched successfully", data);
    }

    @GetMapping("/get-my-eranings")
    public ResponseEntity<?> getMyEarnings(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "pending", required = false) String pending) {
        UUID driverId = requireAuthDriverId();
        int effectivePage = page != null && page > 0 ? page : 1;
        int effectiveLimit = limit != null && limit > 0 ? limit : 10;
        boolean isPending = "true".equalsIgnoreCase(pending);
        JsonNode data = driverReportService.getMyEarnings(driverId, effectivePage, effectiveLimit, isPending);
        return apiResponseService.ok("Earnings fetched successfully", data);
    }

    @GetMapping("/get-my-referral-data")
    public ResponseEntity<?> getMyReferralData() {
        UUID driverId = requireAuthDriverId();
        Map<String, Object> data = driverReportService.getMyReferralData(driverId);
        return apiResponseService.ok("Referral data fetched successfully", data);
    }

    @GetMapping("/referral-earnings")
    public ResponseEntity<?> getReferralEarnings(@RequestParam(value = "driverId", required = false) String driverIdParam) {
        UUID driverId = resolveDriverId(driverIdParam, "Valid driver id is required");
        JsonNode data = driverReportService.getReferralEarnings(driverId);
        return apiResponseService.ok("Referral earnings fetched successfully", data);
    }

    @GetMapping("/driver-referrals")
    public ResponseEntity<?> getDriverReferralsList(@RequestParam(value = "driverId", required = false) String driverIdParam) {
        UUID driverId = resolveDriverId(driverIdParam, "Invalid driverId");
        List<Map<String, Object>> data = driverReportService.getDriverReferralsList(driverId);
        return apiResponseService.ok("Driver referrals fetched successfully", data);
    }

    @GetMapping("/ref-driver-payment-details")
    public ResponseEntity<?> getRefDriverPaymentDetails(@RequestParam(value = "refDriverId", required = false) String refDriverIdParam) {
        UUID ownerDriverId = requireAuthDriverId();
        UUID refDriverId = resolveDriverId(refDriverIdParam, "Valid refDriverId is required");
        JsonNode data = driverReportService.getRefDriverPaymentDetails(ownerDriverId, refDriverId);
        return apiResponseService.ok("Referral driver payment details fetched successfully", data);
    }

    @GetMapping("/dashboard-stats")
    public ResponseEntity<?> getDriverDashboardStats() {
        UUID driverId = requireAuthDriverId();
        Map<String, Object> data = driverReportService.getDriverDashboardStats(driverId);
        return apiResponseService.ok("Driver dashboard stats fetched successfully", data);
    }

    @GetMapping("/get-drivers-report")
    public ResponseEntity<?> getDriversReport() {
        Map<String, Object> data = driverReportService.getDriversReport();
        return apiResponseService.ok("Drivers report fetched successfully", data);
    }

    @PostMapping("/get-drivers-ride-report")
    public ResponseEntity<?> getDriversRideReport(@RequestBody(required = false) GetDriversRideReportRequest request) {
        Map<String, Object> data = driverReportService.getDriversRideReport(
                request != null ? request : new GetDriversRideReportRequest());
        return apiResponseService.ok("Drivers fetched successfully", data);
    }

    @PatchMapping("/referral")
    public ResponseEntity<?> updateReferral(@Valid @RequestBody UpdateReferralRequest request) {
        UUID driverId = resolveDriverId(
                AuthContext.current() != null ? AuthContext.current().principalId() : null,
                request.getDriverId(),
                "Driver id is not valid");
        driverReportService.updateReferral(driverId, request.getReferralCode());
        return apiResponseService.ok("Referred successfully", Map.of());
    }

    /**
     * File download endpoint - the original export_report bypasses sendResponse entirely (raw
     * CSV/PDF/XLSX bytes, or a plain unencrypted {@code {message}} JSON body on error), so this
     * one deliberately does NOT go through {@link ApiResponseService} to preserve that exact
     * behaviour instead of wrapping/encrypting a file download.
     */
    @GetMapping("/export")
    public ResponseEntity<?> exportReport(
            @RequestParam("type") String type,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String docStatus,
            @RequestParam(required = false) String driverStatus,
            @RequestParam(required = false) String genderFilter,
            @RequestParam(required = false) String blockFilter,
            @RequestParam(required = false) String nameSorting) {
        DriverExportReportResult result = driverReportService.exportReport(
                type, search, docStatus, driverStatus, genderFilter, blockFilter, nameSorting);
        if (!result.success()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("message", result.errorMessage()));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, result.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + result.filename())
                .body(result.body());
    }

    private UUID requireAuthDriverId() {
        AuthContext ctx = AuthContext.current();
        if (ctx == null || ctx.principalId() == null) {
            throw new ApiException(StatusCode.UNAUTHORIZED, "Unauthorized access");
        }
        return parseUuid(ctx.principalId(), "Unauthorized access", StatusCode.UNAUTHORIZED);
    }

    private UUID resolveDriverId(String queryParam, String invalidMessage) {
        AuthContext ctx = AuthContext.current();
        String id = (ctx != null && ctx.principalId() != null) ? ctx.principalId() : queryParam;
        if (id == null || id.isBlank()) {
            throw new ApiException(StatusCode.BAD_REQUEST, invalidMessage);
        }
        return parseUuid(id, invalidMessage, StatusCode.BAD_REQUEST);
    }

    private UUID resolveDriverId(String authPrincipalId, String bodyDriverId, String invalidMessage) {
        String id = authPrincipalId != null ? authPrincipalId : bodyDriverId;
        if (id == null || id.isBlank()) {
            throw new ApiException(StatusCode.UNPROCESSABLE_ENTITY, invalidMessage);
        }
        return parseUuid(id, invalidMessage, StatusCode.UNPROCESSABLE_ENTITY);
    }

    private UUID parseUuid(String value, String invalidMessage, int statusCode) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ApiException(statusCode, invalidMessage);
        }
    }
}
