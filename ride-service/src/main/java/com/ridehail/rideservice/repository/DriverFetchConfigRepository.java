package com.ridehail.rideservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ridehail.rideservice.entity.DriverFetchConfig;

/** Mirrors get_driver_fetch_config/update_driver_fetch_config in ride.controller.js - a small, effectively singleton config document. */
public interface DriverFetchConfigRepository extends JpaRepository<DriverFetchConfig, UUID> {

    Optional<DriverFetchConfig> findFirstByOrderByCreatedAtDesc();
}
