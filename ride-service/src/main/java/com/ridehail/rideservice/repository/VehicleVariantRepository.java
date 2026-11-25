package com.ridehail.rideservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.ridehail.rideservice.entity.VehicleVariant;

/** Mirrors vehicle_variant_list/add_vehicle_variant/update_vehicle_variant in vehicle.controller.js/vehicle.service.js. */
public interface VehicleVariantRepository extends JpaRepository<VehicleVariant, UUID>,
        JpaSpecificationExecutor<VehicleVariant> {

    List<VehicleVariant> findByDeletedFalse();

    List<VehicleVariant> findByVehicleTypeIdAndDeletedFalse(UUID vehicleTypeId);

    long countByDeletedFalse();

    long countByDeletedFalseAndLocalTrue();

    long countByDeletedFalseAndOutstationTrue();

    Optional<VehicleVariant> findByIdAndDeletedFalse(UUID id);

    List<VehicleVariant> findByDeletedFalseAndLocalTrueAndLocalPriceIdNotNullOrderByCreatedAtDesc();

    List<VehicleVariant> findByDeletedFalseAndOutstationTrueAndOutstationPriceIdNotNullOrderByCreatedAtDesc();
}
