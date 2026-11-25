package com.ridehail.userservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ridehail.userservice.entity.UserActivity;

/** Backs service/user.services.js's saveActivity (helpers/user.commonFunction.js#logActivity). */
@Repository
public interface UserActivityRepository extends JpaRepository<UserActivity, UUID> {

    List<UserActivity> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
