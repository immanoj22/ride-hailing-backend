package com.ridehail.driverservice.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ridehail.common.exception.ApiException;
import com.ridehail.common.response.ApiResponseService;
import com.ridehail.common.security.AuthContext;
import com.ridehail.driverservice.dto.DeleteAccountRequest;
import com.ridehail.driverservice.dto.DriverForNotificationRequest;
import com.ridehail.driverservice.dto.DriverRegisterRequest;
import com.ridehail.driverservice.dto.LogoutDriverRequest;
import com.ridehail.driverservice.dto.ResendDriverOtpRequest;
import com.ridehail.driverservice.dto.SendDriverOtpRequest;
import com.ridehail.driverservice.service.DriverAuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Direct port of driver.controller.js's driver_register / send_Driver_Otp / resend_Driver_Otp /
 * logout_driver / delete_account / getDriverForNotification (routes/driver.routes.js "AUTH FREE
 * ROUTES" group, mounted under the {@code /driver-apis} prefix from server.js).
 */
@RestController
@RequestMapping("/driver-apis")
public class DriverAuthController {

    private final DriverAuthService driverAuthService;
    private final ApiResponseService apiResponseService;

    public DriverAuthController(DriverAuthService driverAuthService, ApiResponseService apiResponseService) {
        this.driverAuthService = driverAuthService;
        this.apiResponseService = apiResponseService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody DriverRegisterRequest req, HttpServletRequest request) {
        if ("phone".equals(req.getMethod())) {
            Map<String, Object> data = driverAuthService.registerPhone(request, req.getMobile(), req.getOtp(), req.getReferredBy());
            return apiResponseService.ok("Login successful", data);
        }
        if ("google".equals(req.getMethod())) {
            Map<String, Object> data = driverAuthService.registerGoogle(request, req.getToken(), req.getFcm(), req.getReferredBy());
            return apiResponseService.ok("Driver registered successfully", data);
        }
        throw new ApiException(400, "method is required");
    }

    @PostMapping("/send-driver-otp")
    public ResponseEntity<?> sendDriverOtp(@RequestBody SendDriverOtpRequest req) {
        Map<String, Object> data = driverAuthService.sendOtp(req.getMobile(), req.getFcmToken());
        return apiResponseService.ok("OTP sent", data);
    }

    @PostMapping("/resend-driver-otp")
    public ResponseEntity<?> resendDriverOtp(@RequestBody ResendDriverOtpRequest req) {
        driverAuthService.resendOtp(req.getMobile());
        return apiResponseService.ok("OTP resent successfully", Map.of());
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody LogoutDriverRequest req, HttpServletRequest request) {
        driverAuthService.logout(request, req.getDriverId());
        return apiResponseService.ok("Logged out successfully", Map.of());
    }

    @DeleteMapping("/delete-account")
    public ResponseEntity<?> deleteAccount(@RequestBody(required = false) DeleteAccountRequest req, HttpServletRequest request) {
        String driverId = AuthContext.current() == null ? null : AuthContext.current().principalId();
        String deleteReason = req == null ? null : req.getDeleteReason();
        driverAuthService.deleteAccount(request, driverId, deleteReason);
        return apiResponseService.ok("Account deleted successfully", Map.of());
    }

    @PostMapping("/get-drivers-for-notification")
    public ResponseEntity<?> getDriversForNotification(@RequestBody DriverForNotificationRequest req) {
        List<?> data = driverAuthService.getDriversForNotification(req.getAction(), req.getMobile());
        return apiResponseService.ok("Users fetched successfully", data);
    }
}
