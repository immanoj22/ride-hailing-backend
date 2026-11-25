package com.ridehail.userservice.controller;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ridehail.common.response.ApiResponseService;
import com.ridehail.userservice.dto.RegisterRequest;
import com.ridehail.userservice.dto.RegisterSendOtpRequest;
import com.ridehail.userservice.dto.UpdateFcmRequest;
import com.ridehail.userservice.dto.UpdateLocationRequest;
import com.ridehail.userservice.dto.UpdatePreferencesRequest;
import com.ridehail.userservice.dto.UpdateProfileRequest;
import com.ridehail.userservice.service.AuthProfileService;
import com.ridehail.userservice.util.RequestMetaUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * "auth-profile-session" bucket - direct port of routes/user.routes.js's register/profile/session
 * routes (mounted under "/user-apis" by server.js). No business logic here - see
 * {@link AuthProfileService}.
 */
@RestController
@RequestMapping("/user-apis")
public class AuthProfileController {

    private final AuthProfileService authProfileService;
    private final ApiResponseService apiResponseService;

    public AuthProfileController(AuthProfileService authProfileService, ApiResponseService apiResponseService) {
        this.authProfileService = authProfileService;
        this.apiResponseService = apiResponseService;
    }

    /** PUT /register-send-otp. */
    @PutMapping("/register-send-otp")
    public ResponseEntity<?> registerSendOtp(@Valid @RequestBody RegisterSendOtpRequest request) {
        Map<String, Object> data = authProfileService.registerSendOtp(request);
        return apiResponseService.ok("OTP sent to your registered email ID or phone number.", data);
    }

    /** POST /register. */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        Map<String, Object> data = authProfileService.register(request);
        return apiResponseService.ok("User registered successfully", data);
    }

    /** GET /get-user-by-token. */
    @GetMapping("/get-user-by-token")
    public ResponseEntity<?> getUserProfileByUser(
            @RequestParam(value = "fcm", required = false) String fcm,
            @RequestParam(value = "isRideCheck", required = false) String isRideCheck) {
        var data = authProfileService.getUserProfileByUser(fcm, Boolean.parseBoolean(isRideCheck));
        return apiResponseService.ok("Profile fetched", Map.of("user", data));
    }

    /** POST /update-profile. Original accepts an optional multipart "imageFiles" field alongside
     *  the JSON-ish form fields; consumes multipart/form-data to support both. */
    @PostMapping(value = "/update-profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateProfileMultipart(
            UpdateProfileRequest request,
            @RequestParam(value = "imageFiles", required = false) MultipartFile imageFiles,
            HttpServletRequest httpRequest) {
        var data = authProfileService.updateProfile(request, imageFiles, RequestMetaUtil.extract(httpRequest));
        return apiResponseService.ok("Profile updated", Map.of("user", data));
    }

    /** POST /update-profile without a file attached (plain JSON body). */
    @PostMapping(value = "/update-profile", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateProfileJson(@RequestBody UpdateProfileRequest request, HttpServletRequest httpRequest) {
        var data = authProfileService.updateProfile(request, null, RequestMetaUtil.extract(httpRequest));
        return apiResponseService.ok("Profile updated", Map.of("user", data));
    }

    /** POST /update-fcm. */
    @PostMapping("/update-fcm")
    public ResponseEntity<?> updateFcm(@Valid @RequestBody UpdateFcmRequest request, HttpServletRequest httpRequest) {
        var data = authProfileService.updateFcm(request, RequestMetaUtil.extract(httpRequest));
        return apiResponseService.ok("FCM token updated", Map.of("user", data));
    }

    /** POST /update-location. */
    @PostMapping("/update-location")
    public ResponseEntity<?> updateLocation(@Valid @RequestBody UpdateLocationRequest request, HttpServletRequest httpRequest) {
        Map<String, Object> data = authProfileService.updateLocation(request, RequestMetaUtil.extract(httpRequest));
        return apiResponseService.ok("Location updated", data);
    }

    /** POST /update-preferences. */
    @PostMapping("/update-preferences")
    public ResponseEntity<?> updatePreferences(@Valid @RequestBody UpdatePreferencesRequest request, HttpServletRequest httpRequest) {
        var data = authProfileService.updatePreferences(request, RequestMetaUtil.extract(httpRequest));
        return apiResponseService.ok("Preferences updated", Map.of("user", data));
    }

    /** GET /profile/{userId}. */
    @GetMapping("/profile/{userId}")
    public ResponseEntity<?> getProfile(@PathVariable String userId) {
        var data = authProfileService.getProfile(userId);
        return apiResponseService.ok("Profile fetched", Map.of("user", data));
    }

    /** GET /user-location/{userId}. */
    @GetMapping("/user-location/{userId}")
    public ResponseEntity<?> getUserLocation(@PathVariable String userId) {
        Map<String, Object> data = authProfileService.getUserLocation(userId);
        return apiResponseService.ok("User location fetched", data);
    }

    /** GET /logout?userId=. */
    @GetMapping("/logout")
    public ResponseEntity<?> userLogout(@RequestParam("userId") String userId, HttpServletRequest httpRequest) {
        var data = authProfileService.userLogout(userId, RequestMetaUtil.extract(httpRequest));
        return apiResponseService.ok("Logged out", Map.of("user", data));
    }

    /** POST /delete?userId=. */
    @PostMapping("/delete")
    public ResponseEntity<?> userDelete(@RequestParam("userId") String userId, HttpServletRequest httpRequest) {
        var data = authProfileService.userDelete(userId, RequestMetaUtil.extract(httpRequest));
        return apiResponseService.ok("User deleted", Map.of("user", data));
    }
}
