package com.ridehail.driverservice.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.common.security.InternalServiceClient;
import com.ridehail.common.security.JwtService;
import com.ridehail.driverservice.dto.DriverNotificationTargetResponse;
import com.ridehail.driverservice.entity.Driver;
import com.ridehail.driverservice.repository.DriverRepository;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Equivalent of driver.controller.js's driver_register / send_Driver_Otp / resend_Driver_Otp /
 * logout_driver / delete_account / getDriverForNotification.
 */
@Service
public class DriverAuthService {

    private static final String HARDCODED_OTP = "123456"; // matches the original's commented-out generateOTP()

    private final DriverRepository driverRepository;
    private final DriverCreationHelper driverCreationHelper;
    private final DriverActivityLogger activityLogger;
    private final DriverReferralRewardService referralRewardService;
    private final DriverResponseMapper responseMapper;
    private final JwtService jwtService;
    private final InternalServiceClient internalServiceClient;
    private final String driverJwtSecret;
    private final String commonServiceUrl;

    public DriverAuthService(
            DriverRepository driverRepository,
            DriverCreationHelper driverCreationHelper,
            DriverActivityLogger activityLogger,
            DriverReferralRewardService referralRewardService,
            DriverResponseMapper responseMapper,
            JwtService jwtService,
            InternalServiceClient internalServiceClient,
            @Value("${ridehail.jwt.driver-secret}") String driverJwtSecret,
            @Value("${ridehail.services.common-url}") String commonServiceUrl) {
        this.driverRepository = driverRepository;
        this.driverCreationHelper = driverCreationHelper;
        this.activityLogger = activityLogger;
        this.referralRewardService = referralRewardService;
        this.responseMapper = responseMapper;
        this.jwtService = jwtService;
        this.internalServiceClient = internalServiceClient;
        this.driverJwtSecret = driverJwtSecret;
        this.commonServiceUrl = commonServiceUrl;
    }

    @Transactional
    public Map<String, Object> registerPhone(HttpServletRequest request, String mobile, String otp, String referredBy) {
        if (mobile == null || mobile.isBlank()) {
            throw new ApiException(400, "Mobile number is required");
        }
        if (otp == null || otp.isBlank()) {
            throw new ApiException(400, "OTP is required");
        }

        String normalizedMobile = "+91" + mobile;
        Driver driver = driverRepository.findByMobileAndDeletedFalse(normalizedMobile).orElse(null);
        if (driver == null) {
            throw new ApiException(404, "Driver not found. Please request OTP again");
        }
        if (driver.getOtp() == null || driver.getOtp().isBlank() || driver.getOtpExpiresAt() == null) {
            throw new ApiException(400, "OTP not requested");
        }
        if (driver.getOtpExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new ApiException(400, "OTP expired");
        }

        boolean isClientTestOtp = "+919786248882".equals(driver.getMobile()) && "000000".equals(otp);
        if (!driver.getOtp().equals(otp) && !isClientTestOtp) {
            throw new ApiException(400, "Invalid OTP");
        }

        if (referredBy != null && !referredBy.isBlank() && driver.getReferredBy() == null) {
            referralRewardService.applyReferralReward(driver.getId(), referredBy);
        }

        driver.setOtp("");
        driver.setOtpExpiresAt(null);
        driver = driverRepository.save(driver);

        String token = jwtService.generate(Map.of("id", driver.getId().toString()), driverJwtSecret, 0);
        activityLogger.log(request, driver.getId(), "loginWithMobileOtp");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("driver", responseMapper.toResponseWithDocsAndContacts(driver));
        data.put("token", token);
        return data;
    }

    @Transactional
    public Map<String, Object> registerGoogle(HttpServletRequest request, String token, String fcm, String referredBy) {
        if (token == null || token.isBlank()) {
            throw new ApiException(400, "Google token missing");
        }

        JsonNode googleResp = internalServiceClient.post(commonServiceUrl + "/google-verify", Map.of("token", token));
        JsonNode gUser = googleResp == null ? null : googleResp.path("data");
        String email = gUser == null ? null : gUser.path("email").asText(null);
        if (gUser == null || gUser.isMissingNode() || email == null || email.isBlank()) {
            throw new ApiException(400, "Invalid Google token");
        }

        boolean isNewUser = false;
        Driver driver = driverRepository.findByEmail(email).orElse(null);
        if (driver == null) {
            String referralCode = driverCreationHelper.generateUniqueReferralCode();
            Driver newDriver = Driver.builder()
                    .email(email)
                    .profileImage(gUser.path("picture").asText(""))
                    .referralCode(referralCode)
                    .fcmToken(fcm == null ? "" : fcm)
                    .build();
            driver = driverCreationHelper.save(newDriver);
            if (referredBy != null && !referredBy.isBlank()) {
                referralRewardService.applyReferralReward(driver.getId(), referredBy);
            }
            isNewUser = true;
        }
        // Existing driver: the original sets an undeclared "fcm" (not "fcmToken") field on the
        // Mongoose document before saving, which Mongoose's default strict-schema mode silently
        // drops - i.e. fcmToken is NOT actually updated for returning Google-login drivers. This
        // is preserved as a no-op here rather than "fixed" to actually persist the new fcm token.

        activityLogger.log(request, driver.getId(), "register");
        String driverJwt = jwtService.generate(Map.of("id", driver.getId().toString()), driverJwtSecret, 0);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("driver", responseMapper.toResponseWithDocsAndContacts(driver));
        data.put("token", driverJwt);
        data.put("newUser", isNewUser);
        return data;
    }

    @Transactional
    public Map<String, Object> sendOtp(String mobile, String fcmToken) {
        if (mobile == null || mobile.isBlank()) {
            throw new ApiException(404, "Mobile number is required");
        }

        String normalizedMobile = "+91" + mobile;
        Driver driver = driverRepository.findByMobileAndDeletedFalse(normalizedMobile).orElse(null);
        boolean newUser = false;
        if (driver == null) {
            String referralCode = driverCreationHelper.generateUniqueReferralCode();
            Driver newDriver = Driver.builder()
                    .mobile(normalizedMobile)
                    .referralCode(referralCode)
                    .fcmToken(fcmToken == null ? "" : fcmToken)
                    .build();
            driver = driverCreationHelper.save(newDriver);
            newUser = true;
        }

        driver.setOtp(HARDCODED_OTP);
        driver.setOtpExpiresAt(OffsetDateTime.now().plusMinutes(5));
        if (fcmToken != null && !fcmToken.isBlank()) {
            driver.setFcmToken(fcmToken);
        }
        driverRepository.save(driver);

        return Map.of("newUser", newUser);
    }

    @Transactional
    public void resendOtp(String mobile) {
        if (mobile == null || mobile.isBlank()) {
            throw new ApiException(400, "Mobile number is required");
        }
        String normalizedMobile = "+91" + mobile;
        Driver driver = driverRepository.findByMobileAndDeletedFalse(normalizedMobile)
                .orElseThrow(() -> new ApiException(404, "Driver not found"));

        driver.setOtp(HARDCODED_OTP);
        driver.setOtpExpiresAt(OffsetDateTime.now().plusMinutes(5));
        driverRepository.save(driver);
    }

    @Transactional
    public void logout(HttpServletRequest request, String driverIdRaw) {
        if (driverIdRaw == null || driverIdRaw.isBlank()) {
            throw new ApiException(400, "driverId is required");
        }
        UUID driverId = parseUuid(driverIdRaw, "driverId");
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ApiException(404, "Driver not found"));

        driver.setFcmToken("");
        driver.setSocketId("");
        driver.setSocketOnline(false);
        driver.setDriverStatus("offline");
        driver.setOnRide(false);
        driverRepository.save(driver);

        activityLogger.log(request, driverId, "logoutDriver", activityLogger.mapper().createObjectNode()
                .put("message", "Driver logged out from device"));
    }

    @Transactional
    public void deleteAccount(HttpServletRequest request, String driverIdRaw, String deleteReason) {
        if (driverIdRaw == null || driverIdRaw.isBlank()) {
            throw new ApiException(401, "Unauthorized access");
        }
        UUID driverId = parseUuid(driverIdRaw, "driverId");
        Driver driver = driverRepository.findById(driverId)
                .filter(d -> !d.isDeleted())
                .orElseThrow(() -> new ApiException(404, "Driver not found or already deleted"));

        driver.setDeleted(true);
        driver.setDeleteReason(deleteReason == null ? "" : deleteReason.trim());
        driver.setFcmToken("");
        driver.setSocketId("");
        driver.setSocketOnline(false);
        driver.setDriverStatus("offline");
        driver.setOnRide(false);
        driverRepository.save(driver);

        activityLogger.log(request, driverId, "removeUserAccount");
    }

    public List<DriverNotificationTargetResponse> getDriversForNotification(String action, Object mobile) {
        if (action == null || action.isBlank()) {
            throw new ApiException(400, "Action query parameter is required");
        }

        List<Driver> drivers;
        if ("individual".equals(action) && mobile != null) {
            List<String> mobiles = new ArrayList<>();
            if (mobile instanceof List<?> list) {
                for (Object m : list) {
                    mobiles.add("+91" + String.valueOf(m).trim());
                }
            } else {
                mobiles.add("+91" + String.valueOf(mobile).trim());
            }
            drivers = driverRepository.findWithFcmTokenAndMobileIn(mobiles);
        } else if ("all".equals(action)) {
            drivers = driverRepository.findByMobileIsNotNullAndDeletedFalse();
        } else {
            drivers = driverRepository.findAllWithFcmToken();
        }

        List<DriverNotificationTargetResponse> result = new ArrayList<>();
        for (Driver driver : drivers) {
            result.add(new DriverNotificationTargetResponse(driver.getId().toString(), driver.getMobile(), driver.getFcmToken()));
        }
        return result;
    }

    private UUID parseUuid(String value, String fieldName) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, "Invalid " + fieldName);
        }
    }
}
