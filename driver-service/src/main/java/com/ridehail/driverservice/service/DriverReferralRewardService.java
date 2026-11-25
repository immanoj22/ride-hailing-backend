package com.ridehail.driverservice.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ridehail.driverservice.entity.Driver;
import com.ridehail.driverservice.repository.DriverRepository;

/**
 * Equivalent of driver.controller.js's {@code applyDriverReferralReward({driverId, referralCode})}:
 * links {@code referredDriver.referredBy} to the referrer, only when the referral code is valid,
 * isn't self-referral, and the driver hasn't already been referred. The legacy "referrals" array
 * push on the referrer is intentionally not reproduced here - see {@link Driver}'s class notes;
 * it is fully derivable via {@code DriverRepository.findByReferredBy_IdAndDeletedFalse}.
 */
@Service
public class DriverReferralRewardService {

    private final DriverRepository driverRepository;

    public DriverReferralRewardService(DriverRepository driverRepository) {
        this.driverRepository = driverRepository;
    }

    @Transactional
    public UUID applyReferralReward(UUID driverId, String referralCode) {
        if (referralCode == null || referralCode.isBlank()) {
            return null;
        }
        Driver referrer = driverRepository.findByReferralCode(referralCode).orElse(null);
        if (referrer == null || referrer.getId().equals(driverId)) {
            return null;
        }
        Driver referredDriver = driverRepository.findById(driverId).orElse(null);
        if (referredDriver == null || referredDriver.getReferredBy() != null) {
            return null;
        }
        referredDriver.setReferredBy(referrer);
        driverRepository.save(referredDriver);
        return referrer.getId();
    }
}
