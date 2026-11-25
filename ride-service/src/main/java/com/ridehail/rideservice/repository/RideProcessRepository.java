package com.ridehail.rideservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ridehail.rideservice.entity.RideProcess;

/** Child rows of a ride's embedded "process[]" status timeline (ride.schema.js). */
public interface RideProcessRepository extends JpaRepository<RideProcess, UUID> {

    List<RideProcess> findByRideIdOrderByCreatedAtAsc(UUID rideId);
}
