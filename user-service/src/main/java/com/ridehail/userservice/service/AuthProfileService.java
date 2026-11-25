package com.ridehail.userservice.service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.crypto.CryptoJsCompatibleCipher;
import com.ridehail.common.exception.ApiException;
import com.ridehail.common.security.AuthContext;
import com.ridehail.common.security.InternalServiceClient;
import com.ridehail.common.security.JwtService;
import com.ridehail.userservice.dto.RegisterRequest;
import com.ridehail.userservice.dto.RegisterSendOtpRequest;
import com.ridehail.userservice.dto.UpdateFcmRequest;
import com.ridehail.userservice.dto.UpdateLocationRequest;
import com.ridehail.userservice.dto.UpdatePreferencesRequest;
import com.ridehail.userservice.dto.UpdateProfileRequest;
import com.ridehail.userservice.dto.AddressResponse;
import com.ridehail.userservice.dto.UserResponse;
import com.ridehail.userservice.entity.User;
import com.ridehail.userservice.entity.UserActivity;
import com.ridehail.userservice.repository.AddressRepository;
import com.ridehail.userservice.repository.UserActivityRepository;
import com.ridehail.userservice.repository.UserRepository;
import com.ridehail.userservice.util.RequestMetaUtil.RequestMeta;
import com.ridehail.userservice.util.UserActivityAction;

/**
 * "auth-profile-session" bucket - direct port of user.controller.js's
 * user_register_send_otp/user_register/get_user_profile_by_user/update_profile/update_fcm/
 * update_location/update_preferences/get_profile/get_user_location/user_logout/user_delete,
 * backed by service/user.services.js.
 */
@Service
@Transactional(readOnly = true)
public class AuthProfileService {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final UserActivityRepository userActivityRepository;
    private final WalletReferralService walletReferralService;
    private final InternalServiceClient internalServiceClient;
    private final JwtService jwtService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RestTemplate rawRestTemplate;

    private final String userJwtSecret;
    private final String commonServiceUrl;
    private final String rideServiceUrl;
    private final String inAppSecret;
    private final String encSecret;
    private final boolean plaintextEnv;

    public AuthProfileService(
            UserRepository userRepository,
            AddressRepository addressRepository,
            UserActivityRepository userActivityRepository,
            WalletReferralService walletReferralService,
            InternalServiceClient internalServiceClient,
            JwtService jwtService,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RestTemplateBuilder restTemplateBuilder,
            @Value("${ridehail.jwt.user-secret}") String userJwtSecret,
            @Value("${ridehail.services.common-url}") String commonServiceUrl,
            @Value("${ridehail.services.ride-url}") String rideServiceUrl,
            @Value("${ridehail.jwt.in-app-secret}") String inAppSecret,
            @Value("${ridehail.enc-secret}") String encSecret,
            @Value("${ridehail.env}") String env) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.userActivityRepository = userActivityRepository;
        this.walletReferralService = walletReferralService;
        this.internalServiceClient = internalServiceClient;
        this.jwtService = jwtService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.rawRestTemplate = restTemplateBuilder.setConnectTimeout(Duration.ofSeconds(10)).setReadTimeout(Duration.ofSeconds(10)).build();
        this.userJwtSecret = userJwtSecret;
        this.commonServiceUrl = commonServiceUrl;
        this.rideServiceUrl = rideServiceUrl;
        this.inAppSecret = inAppSecret;
        this.encSecret = encSecret;
        this.plaintextEnv = "development".equalsIgnoreCase(env) || "demo".equalsIgnoreCase(env);
    }

    // ---------------------------------------------------------------------------------------
    // PUT /register-send-otp
    // ---------------------------------------------------------------------------------------

    /**
     * SECURITY WARNING preserved verbatim from the original: the OTP is hardcoded to
     * {@code "123456"} for every request (user_register_send_otp's real
     * {@code generateOTP()} call and the actual SMS-gateway send are both commented out in the
     * Node source) - i.e. every mobile number in this deployment can currently "log in" with the
     * OTP 123456. This is reproduced exactly per the "preserve existing behavior" migration brief,
     * but it must be flagged to a human before this endpoint is exposed publicly.
     */
    @Transactional
    public Map<String, Object> registerSendOtp(RegisterSendOtpRequest request) {
        String otp = "123456";
        String mobile = "+91" + request.getMobile();
        long otpExpiry = Instant.now().toEpochMilli() + 5 * 60 * 1000;

        User user = userRepository.findByMobile(mobile).orElse(null);
        boolean isNewUser = false;
        if (user != null) {
            user.setOtp(otp);
            user.setOtpExpire(otpExpiry);
            userRepository.save(user);
        } else {
            User newUser = User.builder()
                    .mobile(mobile)
                    .otp(otp)
                    .referralCode(generateUniqueReferralCode())
                    .otpExpire(otpExpiry)
                    .build();
            userRepository.save(newUser);
            isNewUser = true;
        }

        return Map.of("newUser", isNewUser);
    }

    // ---------------------------------------------------------------------------------------
    // POST /register
    // ---------------------------------------------------------------------------------------

    @Transactional
    public Map<String, Object> register(RegisterRequest request) {
        if ("phone".equals(request.getMethod())) {
            return registerByPhone(request);
        }
        if ("google".equals(request.getMethod())) {
            return registerByGoogle(request);
        }
        // Matches the original: any other method value falls through with no response sent
        // (the commented-out final "Invalid method" response was never enabled), which in
        // Express would hang the request. Surface it as a clear error instead of hanging.
        throw new ApiException(StatusCode.BAD_REQUEST, "Invalid method");
    }

    private Map<String, Object> registerByPhone(RegisterRequest request) {
        String mobile = "+91" + request.getMobile();
        User user = userRepository.findByMobile(mobile)
                .orElseThrow(() -> new ApiException(StatusCode.BAD_REQUEST, "User not found"));

        if (!String.valueOf(request.getOtp()).equals(String.valueOf(user.getOtp()))) {
            throw new ApiException(StatusCode.BAD_REQUEST, "OTP is incorrect");
        }
        if (user.getOtpExpire() != null && user.getOtpExpire() < Instant.now().toEpochMilli()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "OTP has expired");
        }

        if (request.getReferredBy() != null && !request.getReferredBy().isBlank() && user.getReferredBy() == null) {
            UUID referrerId = walletReferralService.applyReferralReward(user.getId(), request.getReferredBy());
            if (referrerId != null) {
                user.setReferredBy(referrerId);
            }
        }
        user.setFcm(request.getFcm());
        userRepository.save(user);

        String token = jwtService.generate(Map.of("id", user.getId().toString()), userJwtSecret, 0);
        logActivity(user.getId(), UserActivityAction.REGISTER, Map.of());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", buildUserResponse(user));
        result.put("token", token);
        return result;
    }

    private Map<String, Object> registerByGoogle(RegisterRequest request) {
        JsonNode googleResp = internalServiceClient.post(commonServiceUrl + "/google-verify", Map.of("token", request.getToken()));
        JsonNode gUser = (googleResp == null) ? null : googleResp.path("data");
        if (gUser == null || gUser.isMissingNode() || !googleResp.path("status").asBoolean(false)) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Invalid Google token");
        }

        String email = gUser.path("email").asText(null);
        boolean isNewUser = false;
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            user = User.builder()
                    .email(email)
                    .profile(gUser.path("picture").asText(""))
                    .otpVerify(true)
                    .referralCode(generateUniqueReferralCode())
                    .fcm(request.getFcm())
                    .build();
            user = userRepository.save(user);
            if (request.getReferredBy() != null && !request.getReferredBy().isBlank()) {
                walletReferralService.applyReferralReward(user.getId(), request.getReferredBy());
            }
            isNewUser = true;
        } else {
            user.setFcm(request.getFcm());
            user.setOtpVerify(true);
            userRepository.save(user);
        }

        logActivity(user.getId(), UserActivityAction.REGISTER, Map.of());
        String token = jwtService.generate(Map.of("id", user.getId().toString()), userJwtSecret, 0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", buildUserResponse(user));
        result.put("token", token);
        result.put("newUser", isNewUser);
        return result;
    }

    private String generateUniqueReferralCode() {
        String code;
        do {
            code = Long.toString(Math.abs(java.util.concurrent.ThreadLocalRandom.current().nextLong()), 36).toUpperCase();
            if (code.length() > 8) {
                code = code.substring(0, 8);
            }
        } while (userRepository.findByReferralCode(code).isPresent());
        return code;
    }

    // ---------------------------------------------------------------------------------------
    // GET /get-user-by-token
    // ---------------------------------------------------------------------------------------

    @Transactional
    public UserResponse getUserProfileByUser(String fcm, boolean isRideCheck) {
        UUID userId = currentUserId();
        User user;
        if (fcm != null && !fcm.isBlank()) {
            user = userRepository.findById(userId).orElseThrow(() -> new ApiException(StatusCode.BAD_REQUEST, "User not found"));
            user.setFcm(fcm);
            user = userRepository.save(user);
        } else {
            user = userRepository.findById(userId).orElseThrow(() -> new ApiException(StatusCode.BAD_REQUEST, "User not found"));
        }

        UserResponse response = buildUserResponse(user);
        if ("true".equalsIgnoreCase(String.valueOf(isRideCheck))) {
            JsonNode rideResp = internalServiceClient.get(rideServiceUrl + "/get-latest-ride-user/" + userId);
            if (rideResp != null && rideResp.path("status").asBoolean(false) && rideResp.has("data")) {
                response.setLatestRide(rideResp.path("data"));
            }
        }
        return response;
    }

    // ---------------------------------------------------------------------------------------
    // POST /update-profile
    // ---------------------------------------------------------------------------------------

    @Transactional
    public UserResponse updateProfile(UpdateProfileRequest request, MultipartFile imageFile, RequestMeta requestMeta) {
        UUID userId = currentUserId();
        if (userId == null && request.getUserid() != null) {
            userId = parseUuid(request.getUserid());
        }
        if (userId == null) {
            throw new ApiException(StatusCode.BAD_REQUEST, "userId is required");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(StatusCode.NOT_FOUND, "User not found"));

        String imageUrl = null;
        if (imageFile != null && !imageFile.isEmpty()) {
            imageUrl = uploadProfileImage(imageFile);
        }

        // Mirrors the original's unconditional $set semantics with Mongoose's undefined-key
        // stripping: a field is only overwritten when present in the request, EXCEPT fullname,
        // which is always recomputed from whatever firstname/lastname were submitted THIS request
        // (defaulting missing ones to "") rather than merged with the stored values.
        if (request.getFirstname() != null) user.setFirstname(request.getFirstname());
        if (request.getLastname() != null) user.setLastname(request.getLastname());
        if (request.getGender() != null) user.setGender(request.getGender());
        if (request.getEmail() != null) user.setEmail(request.getEmail());
        if (request.getMobile() != null) user.setMobile("+91" + request.getMobile());
        if (request.getIsBlocked() != null) user.setDeleted(request.getIsBlocked());
        if (imageUrl != null) user.setProfile(imageUrl);
        String firstnameForFullname = request.getFirstname() == null ? "" : request.getFirstname();
        String lastnameForFullname = request.getLastname() == null ? "" : request.getLastname();
        user.setFullname((firstnameForFullname + " " + lastnameForFullname).trim());

        user = userRepository.save(user);

        logActivity(userId, UserActivityAction.UPDATE_USER, requestMeta, Map.of());
        redisTemplate.delete(profileRouteCacheKey(userId));

        return buildUserResponse(user);
    }

    private String uploadProfileImage(MultipartFile file) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("user-auth-type", "ridehail-in-app");
            headers.set("X-Internal-Secret", inAppSecret);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("imageFiles", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });
            body.add("allSame", "true");
            body.add("sameFolderName", "public_user");

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
            String rawBody = rawRestTemplate.exchange(commonServiceUrl + "/image-upload", HttpMethod.POST, entity, String.class).getBody();
            String json = plaintextEnv ? rawBody : CryptoJsCompatibleCipher.decrypt(rawBody, encSecret);
            JsonNode response = objectMapper.readTree(json);
            if (!response.path("status").asBoolean(false) || !response.path("data").isArray() || response.path("data").isEmpty()) {
                return null;
            }
            return response.path("data").get(0).asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------
    // POST /update-fcm
    // ---------------------------------------------------------------------------------------

    @Transactional
    public UserResponse updateFcm(UpdateFcmRequest request, RequestMeta requestMeta) {
        UUID userId = currentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(StatusCode.NOT_FOUND, "User not found"));
        user.setFcm(request.getFcm());
        user = userRepository.save(user);
        logActivity(userId, UserActivityAction.UPDATE_FCM, requestMeta, Map.of());
        return buildUserResponse(user);
    }

    // ---------------------------------------------------------------------------------------
    // POST /update-location
    // ---------------------------------------------------------------------------------------

    @Transactional
    public Map<String, Object> updateLocation(UpdateLocationRequest request, RequestMeta requestMeta) {
        UUID userId = currentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(StatusCode.NOT_FOUND, "User not found"));
        user.setCurrentLocationLat(request.getLat());
        user.setCurrentLocationLng(request.getLng());
        user = userRepository.save(user);

        try {
            UserResponse cached = buildUserResponse(user);
            redisTemplate.opsForValue().set(profileRouteCacheKey(userId), objectMapper.writeValueAsString(cached), Duration.ofSeconds(6000));
            Map<String, Object> location = Map.of("type", "Point", "coordinates", List.of(request.getLng(), request.getLat()));
            redisTemplate.opsForValue().set(userLocationCacheKey(userId), objectMapper.writeValueAsString(location), Duration.ofSeconds(6000));
        } catch (JsonProcessingException ignored) {
            // Caching is a best-effort optimization in the original too (wrapped in its own try/catch).
        }

        logActivity(userId, UserActivityAction.UPDATE_LOCATION, requestMeta, Map.of("lat", request.getLat(), "lng", request.getLng()));

        return Map.of("location", Map.of("type", "Point", "coordinates", List.of(request.getLng(), request.getLat())));
    }

    // ---------------------------------------------------------------------------------------
    // POST /update-preferences
    // ---------------------------------------------------------------------------------------

    @Transactional
    public UserResponse updatePreferences(UpdatePreferencesRequest request, RequestMeta requestMeta) {
        UUID userId = currentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(StatusCode.NOT_FOUND, "User not found"));

        UpdatePreferencesRequest.Preferences p = request.getPreferences();
        if (p.getAllowSmsInvoice() != null) user.setAllowSmsInvoice(p.getAllowSmsInvoice());
        if (p.getAllowWhatsAppUpdates() != null) user.setAllowWhatsAppUpdates(p.getAllowWhatsAppUpdates());
        if (p.getAllowEmailInvoice() != null) user.setAllowEmailInvoice(p.getAllowEmailInvoice());
        if (p.getAllowMobileNotification() != null) user.setAllowMobileNotification(p.getAllowMobileNotification());
        user = userRepository.save(user);

        logActivity(userId, UserActivityAction.UPDATE_PREFERENCES, requestMeta, Map.of());
        return buildUserResponse(user);
    }

    // ---------------------------------------------------------------------------------------
    // GET /profile/:userId
    // ---------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public UserResponse getProfile(String userId) {
        User user = userRepository.findById(parseUuidOrThrow(userId))
                .orElseThrow(() -> new ApiException(StatusCode.BAD_REQUEST, "User not found"));
        return buildUserResponse(user);
    }

    // ---------------------------------------------------------------------------------------
    // GET /user-location/:userId
    // ---------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Map<String, Object> getUserLocation(String userId) {
        User user = userRepository.findById(parseUuidOrThrow(userId))
                .orElseThrow(() -> new ApiException(StatusCode.NOT_FOUND, "User not found"));

        Map<String, Object> location = new LinkedHashMap<>();
        location.put("lat", user.getCurrentLocationLat());
        location.put("lng", user.getCurrentLocationLng());

        try {
            redisTemplate.opsForValue().set(userLocationCacheKey(user.getId()), objectMapper.writeValueAsString(location), Duration.ofSeconds(600));
        } catch (JsonProcessingException ignored) {
        }

        return location;
    }

    // ---------------------------------------------------------------------------------------
    // GET /logout
    // ---------------------------------------------------------------------------------------

    @Transactional
    public UserResponse userLogout(String userId, RequestMeta requestMeta) {
        UUID id = parseUuidOrThrow(userId);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException(StatusCode.NOT_FOUND, "User not found"));
        user.setSocketId("");
        user.setSocketstatus(false);
        user.setFcm("");
        user = userRepository.save(user);

        redisTemplate.delete(userId);
        logActivity(id, UserActivityAction.LOGOUT_USER, requestMeta, Map.of());

        return buildUserResponse(user);
    }

    // ---------------------------------------------------------------------------------------
    // POST /delete
    // ---------------------------------------------------------------------------------------

    @Transactional
    public UserResponse userDelete(String userId, RequestMeta requestMeta) {
        UUID id = parseUuidOrThrow(userId);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException(StatusCode.NOT_FOUND, "User not found"));
        user.setDeleted(true);
        user = userRepository.save(user);

        logActivity(id, UserActivityAction.REMOVE_USER_ACCOUNT, requestMeta, Map.of());
        return buildUserResponse(user);
    }

    // ---------------------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------------------

    private UserResponse buildUserResponse(User user) {
        List<AddressResponse> addresses = addressRepository.findByUserIdAndDeletedFalse(user.getId())
                .stream().map(AddressResponse::from).collect(Collectors.toList());
        return UserResponse.from(user, addresses);
    }

    private void logActivity(UUID userId, String action, Map<String, Object> meta) {
        logActivity(userId, action, new RequestMeta("", ""), meta);
    }

    private void logActivity(UUID userId, String action, RequestMeta requestMeta, Map<String, Object> meta) {
        String metaJson;
        try {
            metaJson = objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            metaJson = "{}";
        }
        UserActivity activity = UserActivity.builder()
                .userId(userId)
                .action(action)
                .ip(requestMeta.ip())
                .device(requestMeta.device())
                .meta(metaJson)
                .build();
        userActivityRepository.save(activity);
    }

    private UUID currentUserId() {
        AuthContext ctx = AuthContext.current();
        String principalId = ctx == null ? null : ctx.principalId();
        return principalId == null ? null : parseUuid(principalId);
    }

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private UUID parseUuidOrThrow(String raw) {
        UUID id = raw == null ? null : parseUuid(raw);
        if (id == null) {
            throw new ApiException(StatusCode.BAD_REQUEST, "userId is required");
        }
        return id;
    }

    private String profileRouteCacheKey(UUID userId) {
        return "route:/user-apis/profile/" + userId;
    }

    private String userLocationCacheKey(UUID userId) {
        return "/user-apis/user-location/" + userId;
    }
}
