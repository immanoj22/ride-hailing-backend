package com.ridehail.rideservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ridehail.rideservice.entity.RideAdminPayment;

/** Child rows of a ride's embedded "adminPayment[]" array (ride.schema.js). */
public interface RideAdminPaymentRepository extends JpaRepository<RideAdminPayment, UUID> {

    List<RideAdminPayment> findByRideIdOrderByCreatedAtAsc(UUID rideId);
}
