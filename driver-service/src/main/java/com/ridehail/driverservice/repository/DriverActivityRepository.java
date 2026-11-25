package com.ridehail.driverservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ridehail.driverservice.entity.DriverActivity;

public interface DriverActivityRepository extends JpaRepository<DriverActivity, UUID> {

    List<DriverActivity> findByDriver_IdOrderByCreatedAtDesc(UUID driverId);
}
