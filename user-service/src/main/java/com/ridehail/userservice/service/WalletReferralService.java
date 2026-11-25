package com.ridehail.userservice.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehail.common.constants.AuthUserType;
import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.common.security.AuthContext;
import com.ridehail.common.security.InternalServiceClient;
import com.ridehail.userservice.dto.AddRefererRequest;
import com.ridehail.userservice.dto.AddressResponse;
import com.ridehail.userservice.dto.BulkWalletUpdateResult;
import com.ridehail.userservice.dto.ConsumeReferralDiscountRequest;
import com.ridehail.userservice.dto.ConsumeReferralDiscountResponse;
import com.ridehail.userservice.dto.ReferralEarningResponse;
import com.ridehail.userservice.dto.ReferralEarningsResponse;
import com.ridehail.userservice.dto.UpdateUserWalletBalanceRequest;
import com.ridehail.userservice.dto.UpdateWalletRequest;
import com.ridehail.userservice.dto.UserResponse;
import com.ridehail.userservice.entity.User;
import com.ridehail.userservice.entity.UserReferralEarning;
import com.ridehail.userservice.repository.AddressRepository;
import com.ridehail.userservice.repository.UserReferralEarningRepository;
import com.ridehail.userservice.repository.UserRepository;
import com.ridehail.userservice.util.RabbitQueueNames;

/**
 * Business logic for the "wallet-referral-rewards" bucket: add_referer, update_wallet,
 * consume_referral_discount, update_user_wallet_balance, get_referral_earnings
 * (user.controller.js / user.services.js).
 *
 * <p>{@link #applyReferralReward(UUID, String)} is the port of the shared internal helper
 * {@code applyUserReferralReward} (user.controller.js). It is also called from user_register's
 * "phone"/"google" registration flows in the original controller; this method is exposed
 * (package-public, Spring-managed bean) so the registration bucket's service can call it instead
 * of re-implementing the same referral-application logic.</p>
 */
@Service
public class WalletReferralService {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final UserReferralEarningRepository userReferralEarningRepository;
    private final InternalServiceClient internalServiceClient;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String adminServiceUrl;

    public WalletReferralService(
            UserRepository userRepository,
            AddressRepository addressRepository,
            UserReferralEarningRepository userReferralEarningRepository,
            InternalServiceClient internalServiceClient,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            @Value("${ridehail.services.admin-url}") String adminServiceUrl) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.userReferralEarningRepository = userReferralEarningRepository;
        this.internalServiceClient = internalServiceClient;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.adminServiceUrl = adminServiceUrl;
    }

    // ---------------------------------------------------------------------------------------
    // add_referer (PATCH /user-apis/add-refferal)
    // ---------------------------------------------------------------------------------------

    /**
     * Transactional so the (self-invoked) applyReferralReward/addReferralToReferrer calls below
     * share one Postgres transaction/lock scope even though - being plain internal method calls on
     * {@code this} - they don't go through their own separate Spring transactional proxies.
     */
    @Transactional
    public void addReferer(AddRefererRequest request) {
        UUID userId = parseIdOrThrow(request.getId());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(StatusCode.NOT_FOUND, "User not found"));
        if (user.getReferredBy() != null) {
            throw new ApiException(StatusCode.UNPROCESSABLE_ENTITY, "User already referred");
        }

        UUID referrerId = applyReferralReward(userId, request.getReferralCode());

        User checkUser = userRepository.findByReferralCode(request.getReferralCode()).orElse(null);

        if (referrerId == null) {
            throw new ApiException(StatusCode.UNPROCESSABLE_ENTITY, "Referral code is not valid");
        }

        publishReferredNotification(checkUser);
    }

    private void publishReferredNotification(User checkUser) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "referred");
        // Matches the original: checkUser is assumed non-null whenever referrerId is non-null
        // (both come from the same referralCode lookup); a dangling reference here would throw,
        // exactly like the original's unchecked `checkUser._id` access would.
        payload.put("userId", String.valueOf(checkUser.getId()));
        payload.put("user", checkUser.getFcm());
        try {
            String json = objectMapper.writeValueAsString(payload);
            rabbitTemplate.convertAndSend(RabbitQueueNames.PUSH_NOTIFICATION, json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize push-notification payload", e);
        }
    }

    // ---------------------------------------------------------------------------------------
    // applyUserReferralReward (shared helper - also used by the registration bucket)
    // ---------------------------------------------------------------------------------------

    @Transactional
    public UUID applyReferralReward(UUID userId, String referralCode) {
        if (referralCode == null || referralCode.isBlank()) {
            return null;
        }
        User referrer = userRepository.findByReferralCode(referralCode).orElse(null);
        if (referrer == null || referrer.getId().equals(userId)) {
            return null;
        }

        ReferralSettings settings = fetchReferralSettings();
        if (!settings.enabled()) {
            return null;
        }

        // Idempotency guard, matches Mongoose's findOneAndUpdate({_id:userId, referredBy:null}, ...):
        // a user can only be referred once, including repeated OTP requests.
        User referredUser = userRepository.findByIdForUpdate(userId).orElse(null);
        if (referredUser == null || referredUser.getReferredBy() != null) {
            return null;
        }

        boolean walletCreditReward = "wallet_credit".equals(settings.rewardType());
        boolean tripDiscountReward = "trip_discount".equals(settings.rewardType());

        referredUser.setReferredBy(referrer.getId());
        referredUser.setReferralRewardType(settings.rewardType());
        referredUser.setReferralRewardAmount(walletCreditReward ? settings.walletCredit() : BigDecimal.ZERO);
        referredUser.setReferralRewardDiscountPercent(tripDiscountReward ? settings.discountPercent() : BigDecimal.ZERO);
        referredUser.setReferralRewardRemainingTrips(tripDiscountReward ? settings.tripCount() : 0);
        referredUser.setReferralRewardUsedRideIds("[]");
        referredUser.setReferralRewardGrantedAt(OffsetDateTime.now());
        userRepository.save(referredUser);

        UserReferralEarning earning = UserReferralEarning.builder()
                .referrerId(referrer.getId())
                .referredUserId(referredUser.getId())
                .amount(walletCreditReward ? settings.walletCredit() : BigDecimal.ZERO)
                .rewardType(settings.rewardType())
                .discountPercent(tripDiscountReward ? settings.discountPercent() : BigDecimal.ZERO)
                .tripCount(tripDiscountReward ? settings.tripCount() : 0)
                .build();
        userReferralEarningRepository.save(earning);

        // Faithfully reproduces a bug in the original applyUserReferralReward: it builds a
        // "referrerUpdate" payload that would $inc the REFERRER's wallet for a wallet_credit
        // reward, but then never actually sends that payload - only $addToSet on `referrals` is
        // applied to the referrer (see addReferralToReferrer below). Only the REFERRED user ever
        // receives the wallet-credit amount (a few lines down). Do not "fix" this without explicit
        // product sign-off - it changes who gets paid.
        addReferralToReferrer(referrer.getId(), referredUser.getId());

        if (walletCreditReward && settings.walletCredit().compareTo(BigDecimal.ZERO) > 0) {
            userRepository.adjustWalletCurrentAmount(referredUser.getId(), settings.walletCredit());
        }

        return referrer.getId();
    }

    @Transactional
    public void addReferralToReferrer(UUID referrerId, UUID referredUserId) {
        User referrer = userRepository.findByIdForUpdate(referrerId).orElse(null);
        if (referrer == null) {
            return;
        }
        List<String> referrals = readJsonArray(referrer.getReferrals());
        String referredIdText = referredUserId.toString();
        if (!referrals.contains(referredIdText)) {
            referrals.add(referredIdText);
            referrer.setReferrals(writeJsonArray(referrals));
            userRepository.save(referrer);
        }
    }

    /**
     * Port of utills/user.axios.js#getReferralSettings. NOTE: in the original Node source this
     * function is broken (it calls {@code this.axiosFunc(...)} instead of the module-local
     * {@code axiosFunc(...)}; since {@code axiosFunc} was never exported, {@code this.axiosFunc} is
     * undefined and every call throws, is caught, and returns null) - so in production today
     * applyUserReferralReward always falls back to the hardcoded defaults below (referral_enabled
     * treated as true, rewardType "wallet_credit", walletCredit 0, discountPercent 0, tripCount 1).
     * That bug is an artifact of a JS `this`-binding mistake with no Java equivalent to faithfully
     * reproduce, so this port calls the admin-service settings endpoint correctly instead. This is
     * a deliberate behavior change flagged for human sign-off: real settings will now actually be
     * honored (see summary).
     */
    private ReferralSettings fetchReferralSettings() {
        JsonNode response = internalServiceClient.get(adminServiceUrl + "/get-settings");
        JsonNode data = (response == null) ? null : response.path("data");

        boolean enabled = (data == null) || data.path("referral_enabled").asBoolean(true);
        String rewardType = (data == null) ? "" : data.path("customer_referral_reward_type").asText("");
        if (rewardType == null || rewardType.isBlank()) {
            rewardType = "wallet_credit";
        }
        BigDecimal walletCredit = numericField(data, "customer_referral_wallet_credit", BigDecimal.ZERO);
        BigDecimal discountPercent = numericField(data, "customer_referral_discount_percent", BigDecimal.ZERO);
        int tripCount = numericField(data, "customer_referral_discount_trip_count", BigDecimal.ONE).intValue();

        return new ReferralSettings(enabled, rewardType, walletCredit, discountPercent, tripCount);
    }

    private BigDecimal numericField(JsonNode node, String field, BigDecimal defaultValue) {
        if (node == null) {
            return defaultValue;
        }
        JsonNode value = node.path(field);
        if (value.isNumber()) {
            return value.decimalValue();
        }
        if (value.isTextual()) {
            try {
                return new BigDecimal(value.asText());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private record ReferralSettings(boolean enabled, String rewardType, BigDecimal walletCredit,
            BigDecimal discountPercent, int tripCount) {
    }

    // ---------------------------------------------------------------------------------------
    // update_wallet (POST /user-apis/update-wallet)
    // ---------------------------------------------------------------------------------------

    @Transactional
    public UserResponse updateWallet(UpdateWalletRequest request) {
        String rawUserId = request.getUserId();
        if (rawUserId == null || rawUserId.isBlank()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "userId is required");
        }
        if (!isValidUuid(rawUserId)) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Invalid userId");
        }

        Object amount = request.getAmount();
        if (!(amount instanceof Number) && !(amount instanceof String)) {
            throw new ApiException(StatusCode.BAD_REQUEST, "amount is required and must be a number");
        }
        BigDecimal incAmount = toBigDecimalOrNull(amount);
        if (incAmount == null || incAmount.compareTo(BigDecimal.ZERO) == 0) {
            throw new ApiException(StatusCode.BAD_REQUEST, "amount must be a non-zero number");
        }

        UUID userId = UUID.fromString(rawUserId);
        // Mirrors service/user.services.js#updateUserWallet's overdraft guard: a debit is only
        // applied when it would not push the balance below zero.
        int updated = userRepository.adjustWalletCurrentAmount(userId, incAmount);
        if (updated == 0) {
            throw new ApiException(StatusCode.INTERNAL_SERVER_ERROR, "Failed to update wallet");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(StatusCode.INTERNAL_SERVER_ERROR, "Failed to update wallet"));
        List<AddressResponse> addresses = addressRepository.findByUserIdAndDeletedFalse(userId).stream()
                .map(AddressResponse::from)
                .toList();
        return UserResponse.from(user, addresses);
    }

    // ---------------------------------------------------------------------------------------
    // consume_referral_discount (POST /user-apis/consume-referral-discount)
    // ---------------------------------------------------------------------------------------

    @Transactional
    public ConsumeReferralDiscountResponse consumeReferralDiscount(ConsumeReferralDiscountRequest request) {
        String userIdRaw = request.getUserId();
        String rideIdRaw = request.getRideId();
        if (!isValidUuid(userIdRaw) || !isValidUuid(rideIdRaw)) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Valid userId and rideId are required");
        }

        UUID userId = UUID.fromString(userIdRaw);
        User user = userRepository.findByIdForUpdate(userId).orElse(null);

        boolean applied = false;
        BigDecimal discountPercent = BigDecimal.ZERO;
        int remainingTrips = 0;

        if (user != null && "trip_discount".equals(user.getReferralRewardType())
                && user.getReferralRewardRemainingTrips() != null && user.getReferralRewardRemainingTrips() > 0) {
            List<String> usedRideIds = readJsonArray(user.getReferralRewardUsedRideIds());
            if (!usedRideIds.contains(rideIdRaw)) {
                usedRideIds.add(rideIdRaw);
                user.setReferralRewardUsedRideIds(writeJsonArray(usedRideIds));
                user.setReferralRewardRemainingTrips(user.getReferralRewardRemainingTrips() - 1);
                userRepository.save(user);

                applied = true;
                discountPercent = user.getReferralRewardDiscountPercent();
                remainingTrips = user.getReferralRewardRemainingTrips();
            }
        }

        return new ConsumeReferralDiscountResponse(applied,
                discountPercent == null ? BigDecimal.ZERO : discountPercent, remainingTrips);
    }

    // ---------------------------------------------------------------------------------------
    // update_user_wallet_balance (POST /user-apis/update-wallet-balance)
    // ---------------------------------------------------------------------------------------

    @Transactional
    public BulkWalletUpdateResult updateUserWalletBalance(UpdateUserWalletBalanceRequest request) {
        List<UpdateUserWalletBalanceRequest.Adjustment> userData = request.getUserData();
        if (userData == null || userData.isEmpty()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "userData must be a non-empty array");
        }

        long modified = 0;
        for (UpdateUserWalletBalanceRequest.Adjustment adjustment : userData) {
            UUID userId = parseIdOrThrow(adjustment.getUserId());
            BigDecimal amount = adjustment.getCurrentAmount() == null ? BigDecimal.ZERO : adjustment.getCurrentAmount();
            modified += userRepository.incrementWalletBalance(userId, amount);
        }

        return new BulkWalletUpdateResult(modified, modified, 0, 0, 0, true);
    }

    // ---------------------------------------------------------------------------------------
    // get_referral_earnings (GET /user-apis/referral-earnings)
    // ---------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ReferralEarningsResponse getReferralEarnings(String queryUserId) {
        String effectiveUserId = resolveUserId(queryUserId);
        if (effectiveUserId == null || !isValidUuid(effectiveUserId)) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Valid user id is required");
        }
        UUID userId = UUID.fromString(effectiveUserId);

        List<UserReferralEarning> earnings = userReferralEarningRepository.findByReferrerIdOrderByCreatedAtDesc(userId);
        BigDecimal totalEarnings = earnings.stream()
                .map(UserReferralEarning::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<UUID, User> referredUsers = userRepository
                .findAllById(earnings.stream().map(UserReferralEarning::getReferredUserId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<ReferralEarningResponse> mapped = earnings.stream()
                .map(earning -> toReferralEarningResponse(earning, referredUsers.get(earning.getReferredUserId())))
                .toList();

        return new ReferralEarningsResponse(totalEarnings, mapped);
    }

    private String resolveUserId(String queryUserId) {
        AuthContext ctx = AuthContext.current();
        if (ctx != null && ctx.authUserType() == AuthUserType.USER && ctx.principalId() != null) {
            return ctx.principalId();
        }
        return queryUserId;
    }

    private ReferralEarningResponse toReferralEarningResponse(UserReferralEarning earning, User referredUser) {
        ReferralEarningResponse.ReferredUserSummary summary = (referredUser == null) ? null
                : new ReferralEarningResponse.ReferredUserSummary(
                        referredUser.getId(),
                        referredUser.getFirstname(),
                        referredUser.getLastname(),
                        referredUser.getFullname(),
                        referredUser.getMobile(),
                        referredUser.getProfile());
        return new ReferralEarningResponse(
                earning.getId(),
                earning.getReferrerId(),
                summary,
                earning.getAmount(),
                earning.getRewardType(),
                earning.getDiscountPercent(),
                earning.getTripCount(),
                earning.getCreatedAt(),
                earning.getUpdatedAt());
    }

    // ---------------------------------------------------------------------------------------
    // small helpers
    // ---------------------------------------------------------------------------------------

    private boolean isValidUuid(String value) {
        if (value == null) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Mirrors helpers/user.commonFunction.js's throwing {@code ObjectId(id)} helper (default
     *  fieldName "ID"), which the original bulk-update/add-referer paths call with no prior
     *  isObjectId check, so an invalid/missing id surfaces as a generic 500 "Invalid ID". */
    private UUID parseIdOrThrow(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(StatusCode.INTERNAL_SERVER_ERROR, "Invalid ID");
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new ApiException(StatusCode.INTERNAL_SERVER_ERROR, "Invalid ID");
        }
    }

    private BigDecimal toBigDecimalOrNull(Object amount) {
        try {
            return new BigDecimal(String.valueOf(amount).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<String> readJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(objectMapper.readValue(json, new TypeReference<List<String>>() {
            }));
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    private String writeJsonArray(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize JSON array", e);
        }
    }
}
