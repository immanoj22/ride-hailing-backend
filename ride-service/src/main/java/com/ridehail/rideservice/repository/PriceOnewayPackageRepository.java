package com.ridehail.rideservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ridehail.rideservice.entity.PriceOnewayPackage;

/** Child rows of a price's embedded "outstation.oneWayRound.packages[]" array (price.schema.js). */
public interface PriceOnewayPackageRepository extends JpaRepository<PriceOnewayPackage, UUID> {

    List<PriceOnewayPackage> findByPriceIdOrderByFromKmAsc(UUID priceId);
}
