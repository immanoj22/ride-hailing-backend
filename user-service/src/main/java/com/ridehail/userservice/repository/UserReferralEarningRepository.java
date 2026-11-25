package com.ridehail.userservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ridehail.userservice.entity.UserReferralEarning;

/** Backs user.controller.js's applyUserReferralReward and get_referral_earnings. */
@Repository
public interface UserReferralEarningRepository extends JpaRepository<UserReferralEarning, UUID> {

    List<UserReferralEarning> findByReferrerIdOrderByCreatedAtDesc(UUID referrerId);

    Optional<UserReferralEarning> findByReferredUserId(UUID referredUserId);
}
