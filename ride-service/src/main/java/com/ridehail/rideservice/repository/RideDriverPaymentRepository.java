package com.ridehail.rideservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ridehail.rideservice.entity.RideDriverPayment;

/** Child rows of a ride's embedded "driverPayment[]" array (ride.schema.js). */
public interface RideDriverPaymentRepository extends JpaRepository<RideDriverPayment, UUID> {

    List<RideDriverPayment> findByRideIdOrderByCreatedAtAsc(UUID rideId);
}
