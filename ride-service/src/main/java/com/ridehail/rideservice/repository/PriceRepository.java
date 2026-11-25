package com.ridehail.rideservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.ridehail.rideservice.entity.Price;

/** Mirrors price_list/add_price/update_price/getPriceDetailsById in vehicle.controller.js/ride.controller.js. */
public interface PriceRepository extends JpaRepository<Price, UUID>, JpaSpecificationExecutor<Price> {

    List<Price> findByDeletedFalse();

    List<Price> findByVehicleTypeIdAndDeletedFalse(UUID vehicleTypeId);

    Optional<Price> findByVehicleTypeIdAndVehicleVariantIdAndDeletedFalse(UUID vehicleTypeId, UUID vehicleVariantId);

    Optional<Price> findByIdAndDeletedFalse(UUID id);
}
