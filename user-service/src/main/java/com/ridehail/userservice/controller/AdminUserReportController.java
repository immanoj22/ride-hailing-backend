package com.ridehail.userservice.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ridehail.common.response.ApiResponseService;
import com.ridehail.userservice.dto.ExportReportResult;
import com.ridehail.userservice.dto.GetUserNotificationRequest;
import com.ridehail.userservice.dto.GetUsersListRequest;
import com.ridehail.userservice.dto.GetUsersRideReportRequest;
import com.ridehail.userservice.service.AdminUserReportService;

/**
 * Bucket "admin-reporting-notifications": direct port of the following user.controller.js
 * functions (routes/user.routes.js, mounted at "/user-apis" by server.js):
 * <ul>
 *     <li>{@code POST /get-users-list} -&gt; get_users_list</li>
 *     <li>{@code GET  /get-users-report} -&gt; get_users_report</li>
 *     <li>{@code POST /get-users-ride-report} -&gt; get_users_ride_report</li>
 *     <li>{@code POST /get-users-details} -&gt; get_users_details</li>
 *     <li>{@code GET  /export} -&gt; export_report</li>
 *     <li>{@code POST /get-user-notifications} -&gt; get_user_notification</li>
 * </ul>
 * No business logic here - everything is delegated to {@link AdminUserReportService}.
 */
@RestController
@RequestMapping("/user-apis")
public class AdminUserReportController {

    private final AdminUserReportService adminUserReportService;
    private final ApiResponseService apiResponseService;

    public AdminUserReportController(AdminUserReportService adminUserReportService, ApiResponseService apiResponseService) {
        this.adminUserReportService = adminUserReportService;
        this.apiResponseService = apiResponseService;
    }

    @PostMapping("/get-users-list")
    public ResponseEntity<?> getUsersList(@RequestBody(required = false) GetUsersListRequest request) {
        Map<String, Object> data = adminUserReportService.getUsersList(
                request != null ? request : new GetUsersListRequest());
        return apiResponseService.ok("Users fetched successfully", data);
    }

    @GetMapping("/get-users-report")
    public ResponseEntity<?> getUsersReport() {
        Map<String, Object> data = adminUserReportService.getUsersReport();
        return apiResponseService.ok("Users fetched successfully", data);
    }

    @PostMapping("/get-users-ride-report")
    public ResponseEntity<?> getUsersRideReport(@RequestBody(required = false) GetUsersRideReportRequest request) {
        Map<String, Object> data = adminUserReportService.getUsersRideReport(
                request != null ? request : new GetUsersRideReportRequest());
        return apiResponseService.ok("Users fetched successfully", data);
    }

    @PostMapping("/get-users-details")
    public ResponseEntity<?> getUsersDetails(@RequestBody(required = false) List<String> userIds) {
        List<Map<String, Object>> data = adminUserReportService.getUsersDetails(userIds);
        return apiResponseService.ok("Users fetched successfully", data);
    }

    @PostMapping("/get-user-notifications")
    public ResponseEntity<?> getUserNotification(@RequestBody(required = false) GetUserNotificationRequest request) {
        List<Map<String, Object>> data = adminUserReportService.getUserNotification(
                request != null ? request : new GetUserNotificationRequest());
        return apiResponseService.ok("Users fetched successfully", data);
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
            @RequestParam(required = false) String genderFilter,
            @RequestParam(required = false) String blockFilter,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate) {
        ExportReportResult result = adminUserReportService.exportReport(type, search, genderFilter, blockFilter, fromDate, toDate);
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
}
