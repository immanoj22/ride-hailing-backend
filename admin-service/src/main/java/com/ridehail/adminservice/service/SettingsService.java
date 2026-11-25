package com.ridehail.adminservice.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ridehail.adminservice.dto.UpdateSettingsRequest;
import com.ridehail.adminservice.entity.Settings;
import com.ridehail.adminservice.repository.SettingsRepository;
import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.exception.ApiException;

/**
 * Port of updateSettings + getSettings (Admin-service/controller/admin.controller.js). The
 * legacy service treats the "settings" collection as a singleton: {@code findOneAndUpdate({}, ...,
 * {upsert:true})} and {@code findOne({})}.
 */
@Service
public class SettingsService {

    private final SettingsRepository settingsRepository;

    public SettingsService(SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    @Transactional
    public Map<String, Object> updateSettings(UpdateSettingsRequest req) {
        Settings settings = settingsRepository.findFirstByOrderByCreatedAtAsc().orElseGet(Settings::new);

        if (req.getIosVersion() != null) settings.setIosVersion(req.getIosVersion());
        if (req.getAndroidVersion() != null) settings.setAndroidVersion(req.getAndroidVersion());
        if (req.getIosLink() != null) settings.setIosLink(req.getIosLink());
        if (req.getAndroidLink() != null) settings.setAndroidLink(req.getAndroidLink());
        if (req.getDriverReferralPercent() != null) settings.setDriverReferralPercent(req.getDriverReferralPercent());
        if (req.getUserReferralPercent() != null) settings.setUserReferralPercent(req.getUserReferralPercent());
        if (req.getReferralEnabled() != null) settings.setReferralEnabled(req.getReferralEnabled());
        if (req.getCustomerReferralRewardType() != null) settings.setCustomerReferralRewardType(req.getCustomerReferralRewardType());
        if (req.getCustomerReferralWalletCredit() != null) settings.setCustomerReferralWalletCredit(req.getCustomerReferralWalletCredit());
        if (req.getCustomerReferralDiscountPercent() != null) settings.setCustomerReferralDiscountPercent(req.getCustomerReferralDiscountPercent());
        if (req.getCustomerReferralDiscountTripCount() != null) settings.setCustomerReferralDiscountTripCount(req.getCustomerReferralDiscountTripCount());
        if (req.getDriverReferralRevenuePercent() != null) settings.setDriverReferralRevenuePercent(req.getDriverReferralRevenuePercent());
        if (req.getAdminPercent() != null) settings.setAdminPercent(req.getAdminPercent());
        if (req.getUserMaxCount() != null) settings.setUserMaxCount(req.getUserMaxCount());
        if (req.getLocalRideKmLimit() != null) settings.setLocalRideKmLimit(req.getLocalRideKmLimit());

        return toMap(settingsRepository.save(settings));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSettings() {
        Settings settings = settingsRepository.findFirstByOrderByCreatedAtAsc()
                .orElseThrow(() -> new ApiException(StatusCode.NOT_FOUND, "Not Found"));
        return toMap(settings);
    }

    /** Original response shape is the raw Mongoose doc, whose keys are the schema's snake_case names. */
    private Map<String, Object> toMap(Settings s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", s.getId() != null ? s.getId().toString() : null);
        m.put("ios_version", s.getIosVersion());
        m.put("android_version", s.getAndroidVersion());
        m.put("ios_link", s.getIosLink());
        m.put("android_link", s.getAndroidLink());
        m.put("driver_referral_percent", s.getDriverReferralPercent());
        m.put("user_referral_percent", s.getUserReferralPercent());
        m.put("referral_enabled", s.isReferralEnabled());
        m.put("customer_referral_reward_type", s.getCustomerReferralRewardType());
        m.put("customer_referral_wallet_credit", s.getCustomerReferralWalletCredit());
        m.put("customer_referral_discount_percent", s.getCustomerReferralDiscountPercent());
        m.put("customer_referral_discount_trip_count", s.getCustomerReferralDiscountTripCount());
        m.put("driver_referral_revenue_percent", s.getDriverReferralRevenuePercent());
        m.put("admin_percent", s.getAdminPercent());
        m.put("user_max_count", s.getUserMaxCount());
        m.put("local_ride_km_limit", s.getLocalRideKmLimit());
        m.put("createdAt", s.getCreatedAt());
        m.put("updatedAt", s.getUpdatedAt());
        return m;
    }
}
