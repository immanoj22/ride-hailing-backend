package com.ridehail.driverservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ridehail.driverservice.entity.DriverEmergencyContact;

public interface DriverEmergencyContactRepository extends JpaRepository<DriverEmergencyContact, UUID> {

    List<DriverEmergencyContact> findByDriver_Id(UUID driverId);
}
