package com.ridehail.rideservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.ridehail.rideservice.entity.VehicleType;

/** Mirrors vehicle_type_list/add_vehicle_type/update_vehicle_type in vehicle.controller.js/vehicle.service.js. */
public interface VehicleTypeRepository extends JpaRepository<VehicleType, UUID>,
        JpaSpecificationExecutor<VehicleType> {

    List<VehicleType> findByDeletedFalse();

    List<VehicleType> findByDeletedFalseAndHidedFalse();

    long countByDeletedFalse();

    Optional<VehicleType> findByTypeIgnoreCaseAndDeletedFalse(String type);

    Optional<VehicleType> findByIdAndDeletedFalse(UUID id);
}
