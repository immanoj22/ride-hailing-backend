package com.ridehail.driverservice.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import com.ridehail.common.response.ApiResponseService;
import com.ridehail.common.security.AuthContext;
import com.ridehail.driverservice.dto.DriverResponse;
import com.ridehail.driverservice.dto.ToggleOnlineStatusRequest;
import com.ridehail.driverservice.dto.UpdateBankDetailsForm;
import com.ridehail.driverservice.dto.UpdateDriverFcmRequest;
import com.ridehail.driverservice.dto.UpdateDriverProfileForm;
import com.ridehail.driverservice.dto.UpdateEmergencyContactRequest;
import com.ridehail.driverservice.dto.UpdateLocationRequest;
import com.ridehail.driverservice.dto.UpdatePreferredLocationRequest;
import com.ridehail.driverservice.service.DriverProfileService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Direct port of driver.controller.js's update_profile / upload_documents / edit_kyc_documents /
 * get_profile / toggle_online_status / get_driver_location / update_location /
 * update_driver_fcm / update_bank_details / update_emergency_contact / updatePrefferedLocation.
 */
@RestController
@RequestMapping("/driver-apis")
public class DriverProfileController {

    private final DriverProfileService driverProfileService;
    private final ApiResponseService apiResponseService;

    public DriverProfileController(DriverProfileService driverProfileService, ApiResponseService apiResponseService) {
        this.driverProfileService = driverProfileService;
        this.apiResponseService = apiResponseService;
    }

    @GetMapping("/profile/{driverId}")
    public ResponseEntity<?> getProfile(@PathVariable String driverId,
            @RequestParam(value = "isRideCheck", required = false) String isRideCheck) {
        boolean rideCheck = "true".equalsIgnoreCase(isRideCheck) || "true".equals(isRideCheck);
        DriverResponse response = driverProfileService.getProfile(driverId, rideCheck);
        return apiResponseService.ok("Driver profile fetched successfully", response);
    }

    @PostMapping(value = "/update-profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateProfile(@ModelAttribute UpdateDriverProfileForm form,
            @RequestParam(value = "driverId", required = false) String bodyDriverId,
            HttpServletRequest request) {
        String driverId = resolveDriverId(bodyDriverId);
        DriverResponse response = driverProfileService.updateProfile(request, driverId, form);
        return apiResponseService.ok("Profile updated successfully", Map.of("driver", response));
    }

    @PostMapping(value = "/update-profile", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateProfileJson(@RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {
        UpdateDriverProfileForm form = new UpdateDriverProfileForm();
        if (body != null) {
            form.setFirstName(asString(body.get("firstName")));
            form.setLastName(asString(body.get("lastName")));
            form.setGender(asString(body.get("gender")));
            form.setVehicleNumber(asString(body.get("vehicleNumber")));
            form.setVehicleType(asString(body.get("vehicleType")));
            form.setVehicleVariant(asString(body.get("vehicleVariant")));
            form.setChassisNumber(asString(body.get("chassisNumber")));
            form.setEngineNumber(asString(body.get("engineNumber")));
            form.setFuelType(asString(body.get("fuelType")));
            form.setState(asString(body.get("state")));
            form.setCity(asString(body.get("city")));
            form.setFcmToken(asString(body.get("fcmToken")));
            form.setEmail(asString(body.get("email")));
            form.setDriverStatus(asString(body.get("driverStatus")));
            form.setMobile(asString(body.get("mobile")));
            if (body.get("onRide") != null) {
                form.setOnRide(Boolean.valueOf(String.valueOf(body.get("onRide"))));
            }
            if (body.get("profileCompleted") != null) {
                form.setProfileCompleted(Boolean.valueOf(String.valueOf(body.get("profileCompleted"))));
            }
            if (body.get("isBlocked") != null) {
                form.setIsBlocked(Boolean.valueOf(String.valueOf(body.get("isBlocked"))));
            }
        }
        String driverId = resolveDriverId(body == null ? null : asString(body.get("driverId")));
        DriverResponse response = driverProfileService.updateProfile(request, driverId, form);
        return apiResponseService.ok("Profile updated successfully", Map.of("driver", response));
    }

    @PostMapping(value = "/kyc-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDocuments(MultipartHttpServletRequest request) {
        String driverId = resolveDriverId(null);
        Map<String, MultipartFile> files = request.getFileMap();
        DriverResponse response = driverProfileService.uploadDocuments(request, driverId, files);
        return apiResponseService.ok("KYC submitted successfully", Map.of("driver", response));
    }

    @PostMapping(value = "/kyc-edit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> editKycDocuments(MultipartHttpServletRequest request) {
        String driverId = resolveDriverId(null);
        Map<String, List<MultipartFile>> files = new LinkedHashMap<>(request.getMultiFileMap());
        DriverResponse response = driverProfileService.editKycDocuments(request, driverId, files);
        return apiResponseService.ok("KYC updated successfully", Map.of("driver", response));
    }

    @PostMapping("/toggle-online")
    public ResponseEntity<?> toggleOnlineStatus(@Valid @RequestBody ToggleOnlineStatusRequest req, HttpServletRequest request) {
        String driverId = resolveDriverId(null);
        DriverResponse response = driverProfileService.toggleOnlineStatus(request, driverId, req.getDriverStatus(), req.getCurrentLocation());
        return apiResponseService.ok("Driver is now " + req.getDriverStatus(), Map.of("driver", response));
    }

    @GetMapping("/driver-location/{driverId}")
    public ResponseEntity<?> getDriverLocation(@PathVariable String driverId) {
        return apiResponseService.ok("Driver location fetched", driverProfileService.getDriverLocation(driverId));
    }

    @PostMapping("/update-location")
    public ResponseEntity<?> updateLocation(@Valid @RequestBody UpdateLocationRequest req, HttpServletRequest request) {
        String driverId = resolveDriverId(req.getDriverId());
        return apiResponseService.ok("Location updated",
                driverProfileService.updateLocation(request, driverId, req.getLat(), req.getLng()));
    }

    @PostMapping("/update-fcm")
    public ResponseEntity<?> updateDriverFcm(@Valid @RequestBody UpdateDriverFcmRequest req, HttpServletRequest request) {
        String driverId = resolveDriverId(req.getDriverId());
        String fcmToken = driverProfileService.updateDriverFcm(request, driverId, req.getFcmToken());
        return apiResponseService.ok("FCM token updated successfully", Map.of("fcmToken", fcmToken));
    }

    @PostMapping(value = "/bank-details", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateBankDetails(@ModelAttribute UpdateBankDetailsForm form, HttpServletRequest request) {
        String driverId = resolveDriverId(null);
        return apiResponseService.ok("Bank details updated",
                Map.of("bankDetails", driverProfileService.updateBankDetails(request, driverId, form)));
    }

    @PostMapping(value = "/bank-details", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateBankDetailsJson(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        UpdateBankDetailsForm form = new UpdateBankDetailsForm();
        form.setHolderName(asString(body.get("holderName")));
        form.setAccountNumber(asString(body.get("accountNumber")));
        form.setConfirmAccountNumber(asString(body.get("confirmAccountNumber")));
        form.setIfsc(asString(body.get("ifsc")));
        form.setBankName(asString(body.get("bankName")));
        form.setBranch(asString(body.get("branch")));
        form.setUpiId(asString(body.get("upiId")));
        String driverId = resolveDriverId(null);
        return apiResponseService.ok("Bank details updated",
                Map.of("bankDetails", driverProfileService.updateBankDetails(request, driverId, form)));
    }

    @PostMapping("/emergency-contact")
    public ResponseEntity<?> updateEmergencyContact(@Valid @RequestBody UpdateEmergencyContactRequest req, HttpServletRequest request) {
        String driverId = resolveDriverId(null);
        return apiResponseService.ok("Emergency contact updated", Map.of("emergencyContacts",
                driverProfileService.updateEmergencyContact(request, driverId, req.getName(), req.getRelation(), req.getPhone(), req.getContactId())));
    }

    @PostMapping("/updatePreferredLocation")
    public ResponseEntity<?> updatePreferredLocation(@Valid @RequestBody UpdatePreferredLocationRequest req) {
        String driverId = resolveDriverId(null);
        return apiResponseService.ok("Preferred location is updated",
                Map.of("preferredLocations", driverProfileService.updatePreferredLocation(driverId, req.getPreferredLocations())));
    }

    private String resolveDriverId(String fallback) {
        AuthContext ctx = AuthContext.current();
        if (ctx != null && ctx.principalId() != null) {
            return ctx.principalId();
        }
        return fallback;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
