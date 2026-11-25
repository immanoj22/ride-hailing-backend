package com.ridehail.rideservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ridehail.rideservice.entity.PriceRentalPackage;

/** Child rows of a price's embedded "outstation.rental.packages[]" array (price.schema.js). */
public interface PriceRentalPackageRepository extends JpaRepository<PriceRentalPackage, UUID> {

    List<PriceRentalPackage> findByPriceId(UUID priceId);
}
