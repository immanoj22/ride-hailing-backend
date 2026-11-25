package com.ridehail.adminservice.service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ridehail.adminservice.dto.LoginAdminRequest;
import com.ridehail.adminservice.dto.OtpVerifyRequest;
import com.ridehail.adminservice.dto.RegisterAdminRequest;
import com.ridehail.adminservice.dto.UpdateAdminRequest;
import com.ridehail.adminservice.entity.Admin;
import com.ridehail.adminservice.repository.AdminRepository;
import com.ridehail.adminservice.util.DocView;
import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.common.security.AuthContext;
import com.ridehail.common.security.JwtService;

/**
 * Port of the admin account/auth controller functions in Admin-service/controller/admin.controller.js:
 * register_admin, update_admin, login_admin, logout_admin, getAdminById, get_Admin,
 * forget_password, otp_verify.
 */
@Service
public class AdminService {

    /** Legacy REDIS_KEY.createAdmintemplateKey("otp"). */
    private static final String OTP_KEY = "admin/otp";
    private static final Duration OTP_TTL = Duration.ofSeconds(300);

    private final AdminRepository adminRepository;
    private final ActivityLogger activityLogger;
    private final NotificationQueuePublisher notificationQueuePublisher;
    private final StringRedisTemplate redisTemplate;
    private final JwtService jwtService;
    private final String adminSecret;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(); // bcrypt saltRounds = 10

    public AdminService(AdminRepository adminRepository,
                        ActivityLogger activityLogger,
                        NotificationQueuePublisher notificationQueuePublisher,
                        StringRedisTemplate redisTemplate,
                        JwtService jwtService,
                        @Value("${ridehail.jwt.admin-secret}") String adminSecret) {
        this.adminRepository = adminRepository;
        this.activityLogger = activityLogger;
        this.notificationQueuePublisher = notificationQueuePublisher;
        this.redisTemplate = redisTemplate;
        this.jwtService = jwtService;
        this.adminSecret = adminSecret;
    }

    /* ------------------------------------------------------------------ register */

    @Transactional
    public Map<String, Object> registerAdmin(RegisterAdminRequest req, String ip) {
        String imageUrl = req.getProfile();
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Profile is Empty");
        }
        Admin admin = new Admin();
        admin.setFullname(req.getFullname());
        admin.setEmail(req.getEmail());
        admin.setPassword(passwordEncoder.encode(req.getPassword()));
        admin.setMobileno(req.getMobileno());
        admin.setProfile(imageUrl);
        Admin saved = adminRepository.save(admin);
        activityLogger.log(saved.getId(), "register", ip);
        return DocView.admin(saved);
    }

    /* -------------------------------------------------------------------- update */

    @Transactional
    public Map<String, Object> updateAdmin(UpdateAdminRequest req, String ip) {
        Admin current = currentAdminOrThrow("Not Found");

        String imageUrl = (req.getProfile() != null && !req.getProfile().isBlank()) ? req.getProfile() : null;
        if (imageUrl == null) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Profile is Empty");
        }

        adminRepository.findByEmailAndIdNot(req.getEmail(), current.getId()).ifPresent(a -> {
            throw new ApiException(StatusCode.BAD_REQUEST, "Email Id already Exist");
        });

        String hashPass = (req.getPassword() != null && !req.getPassword().isBlank())
                ? passwordEncoder.encode(req.getPassword())
                : current.getPassword();

        // Original findOneAndUpdate returns the pre-update document; preserve that in the response.
        Map<String, Object> previous = DocView.admin(current);

        current.setFullname(req.getFullname());
        current.setEmail(req.getEmail());
        current.setPassword(hashPass);
        current.setMobileno(req.getMobileno());
        current.setProfile(imageUrl);
        adminRepository.save(current);

        activityLogger.log(current.getId(), "update_admin", ip);
        return previous;
    }

    /* --------------------------------------------------------------------- login */

    @Transactional
    public Map<String, Object> loginAdmin(LoginAdminRequest req, String ip) {
        Admin findAdmin = adminRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new ApiException(StatusCode.BAD_REQUEST, "Invalid email"));

        boolean checkPassword = passwordEncoder.matches(req.getPassword(), findAdmin.getPassword());

        if (!findAdmin.isActive()) {
            findAdmin.setActive(true);
            adminRepository.save(findAdmin);
        }
        if (!checkPassword) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Invalid password");
        }

        String token = jwtService.generate(Map.of("id", findAdmin.getId().toString()), adminSecret, 0);
        activityLogger.log(findAdmin.getId(), "login", ip);

        Map<String, Object> mailContent = new LinkedHashMap<>();
        mailContent.put("name", findAdmin.getFullname());
        mailContent.put("email", req.getEmail());
        mailContent.put("subjectName", "ridehail Application");
        notificationQueuePublisher.sendNotification(Map.of("key", "Login", "mailContent", mailContent));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("findAdmin", DocView.admin(findAdmin));
        data.put("token", token);
        return data;
    }

    /* -------------------------------------------------------------------- logout */

    public void logoutAdmin(String adminId, String ip) {
        if (adminId == null || adminId.isBlank()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "adminId is required");
        }
        Admin findAdmin = parseUuid(adminId)
                .flatMap(adminRepository::findById)
                .orElseThrow(() -> new ApiException(StatusCode.BAD_REQUEST, "Invalid adminId"));
        activityLogger.log(findAdmin.getId(), "logout", ip);
    }

    /* --------------------------------------------------------------- get by id / any */

    @Transactional(readOnly = true)
    public Map<String, Object> getAdminById(String id) {
        Admin admin = parseUuid(id)
                .flatMap(adminRepository::findById)
                .orElseThrow(() -> new ApiException(StatusCode.BAD_REQUEST, "Admin not found"));
        return DocView.adminProjected(admin);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAdmin() {
        Admin admin = adminRepository.findFirstByOrderByCreatedAtAsc()
                .orElseThrow(() -> new ApiException(StatusCode.BAD_REQUEST, "Admin not found"));
        return DocView.adminProjected(admin);
    }

    /* ----------------------------------------------------------- forgot password / otp */

    public void forgetPassword(String email, String ip) {
        Admin findAdmin = currentAdmin();
        if (findAdmin == null && email != null && !email.isBlank()) {
            findAdmin = adminRepository.findByEmail(email).orElse(null);
        }
        if (findAdmin == null) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Invalid adminId");
        }
        String otp = String.valueOf(ThreadLocalRandom.current().nextInt(1000, 10000));
        redisTemplate.opsForValue().set(OTP_KEY, otp, OTP_TTL);
        activityLogger.log(findAdmin.getId(), "forget-password", ip);

        Map<String, Object> mailContent = new LinkedHashMap<>();
        mailContent.put("name", findAdmin.getFullname());
        mailContent.put("email", email);
        mailContent.put("subjectName", "ridehail Application");
        mailContent.put("otp", otp);
        notificationQueuePublisher.sendNotification(Map.of("key", "otp", "mailContent", mailContent));
    }

    public void otpVerify(OtpVerifyRequest req, String ip) {
        Admin findAdmin = currentAdminOrThrow("Invalid adminId");
        String getOtp = redisTemplate.opsForValue().get(OTP_KEY);
        if (getOtp == null || getOtp.isBlank()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "OTP Expired");
        }
        if (!getOtp.equals(String.valueOf(req.getOtpVerify()))) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Incorrect OTP");
        }
        activityLogger.log(findAdmin.getId(), "otp", ip);
    }

    /* -------------------------------------------------------------------- helpers */

    private Admin currentAdmin() {
        AuthContext ctx = AuthContext.current();
        if (ctx == null || ctx.principalId() == null) {
            return null;
        }
        return parseUuid(ctx.principalId()).flatMap(adminRepository::findById).orElse(null);
    }

    private Admin currentAdminOrThrow(String message) {
        Admin admin = currentAdmin();
        if (admin == null) {
            throw new ApiException(StatusCode.BAD_REQUEST, message);
        }
        return admin;
    }

    private static java.util.Optional<UUID> parseUuid(String value) {
        try {
            return java.util.Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            return java.util.Optional.empty();
        }
    }
}
