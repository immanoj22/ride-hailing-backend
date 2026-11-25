package com.ridehail.rideservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.ridehail.rideservice.entity.RideActivity;

/** Mirrors modals/rideactivity.schema.js (Mongoose model "Activity") - used by get_ride_activity_chart. */
public interface RideActivityRepository extends JpaRepository<RideActivity, UUID>,
        JpaSpecificationExecutor<RideActivity> {

    List<RideActivity> findByRideIdOrderByCreatedAtDesc(UUID rideId);
}
