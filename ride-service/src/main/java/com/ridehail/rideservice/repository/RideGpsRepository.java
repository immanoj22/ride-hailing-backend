package com.ridehail.rideservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ridehail.rideservice.entity.RideGps;

/** Mirrors modals/ridegps.schema.js - addGPSPoint/getGPSPoint/get_ongoing_ride_gps in ride.controller.js. */
public interface RideGpsRepository extends JpaRepository<RideGps, UUID> {

    List<RideGps> findByRideIdOrderByRecordedAtAsc(UUID rideId);

    List<RideGps> findByDriverIdOrderByRecordedAtDesc(UUID driverId);

    /** dropoffRide: getRidesGPS({ rideId, driverId, userId }, sort recordedAt asc). */
    List<RideGps> findByRideIdAndDriverIdAndUserIdOrderByRecordedAtAsc(UUID rideId, UUID driverId, UUID userId);

    /** get_ongoing_rides_admin: latest GPS point per ride. */
    java.util.Optional<RideGps> findFirstByRideIdOrderByRecordedAtDescCreatedAtDesc(UUID rideId);

    /** get_ongoing_ride_gps: newest-first GPS points, limited via Pageable. */
    List<RideGps> findByRideIdOrderByRecordedAtDescCreatedAtDesc(UUID rideId, org.springframework.data.domain.Pageable pageable);
}
