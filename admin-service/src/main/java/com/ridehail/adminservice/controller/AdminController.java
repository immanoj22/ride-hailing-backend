package com.ridehail.adminservice.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ridehail.adminservice.dto.LoginAdminRequest;
import com.ridehail.adminservice.dto.OtpVerifyRequest;
import com.ridehail.adminservice.dto.RegisterAdminRequest;
import com.ridehail.adminservice.dto.UpdateAdminRequest;
import com.ridehail.adminservice.service.AdminService;
import com.ridehail.common.response.ApiResponseService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Direct port of the admin-account routes in Admin-service/routes/admin.routes.js
 * (mounted at {@code /admin-apis} by server.js).
 */
@RestController
@RequestMapping("/admin-apis")
public class AdminController {

    private final AdminService adminService;
    private final ApiResponseService apiResponseService;

    public AdminController(AdminService adminService, ApiResponseService apiResponseService) {
        this.adminService = adminService;
        this.apiResponseService = apiResponseService;
    }

    @PostMapping("/register-admin")
    public ResponseEntity<?> registerAdmin(@Valid @RequestBody RegisterAdminRequest req, HttpServletRequest http) {
        Map<String, Object> data = adminService.registerAdmin(req, clientIp(http));
        return apiResponseService.ok("Admin registered successfully", data);
    }

    @PutMapping("/update-admin")
    public ResponseEntity<?> updateAdmin(@Valid @RequestBody UpdateAdminRequest req, HttpServletRequest http) {
        Map<String, Object> data = adminService.updateAdmin(req, clientIp(http));
        return apiResponseService.ok("Admin details updated successfully", data);
    }

    @PostMapping("/login-admin")
    public ResponseEntity<?> loginAdmin(@Valid @RequestBody LoginAdminRequest req, HttpServletRequest http) {
        Map<String, Object> data = adminService.loginAdmin(req, clientIp(http));
        return apiResponseService.ok("Login Successfully", data);
    }

    @PostMapping("/logout-admin")
    public ResponseEntity<?> logoutAdmin(@RequestParam(value = "adminId", required = false) String adminId,
                                         HttpServletRequest http) {
        adminService.logoutAdmin(adminId, clientIp(http));
        return apiResponseService.ok("Logout successful", Map.of());
    }

    @GetMapping("/get-admin/{id}")
    public ResponseEntity<?> getAdminById(@PathVariable("id") String id) {
        return apiResponseService.ok("Admin found successfully.", adminService.getAdminById(id));
    }

    @GetMapping("/get-admin")
    public ResponseEntity<?> getAdmin() {
        return apiResponseService.ok("Admin found successfully.", adminService.getAdmin());
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgetPassword(@RequestParam(value = "email", required = false) String email,
                                            HttpServletRequest http) {
        adminService.forgetPassword(email, clientIp(http));
        return apiResponseService.ok("OTP sent to mail successfully", Map.of());
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> otpVerify(@Valid @RequestBody OtpVerifyRequest req, HttpServletRequest http) {
        adminService.otpVerify(req, clientIp(http));
        return apiResponseService.ok("OTP verified Successfully", Map.of());
    }

    /** Mirrors helpers/admin.commonFunction.js getClientIp(req). */
    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("x-forwarded-for");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
