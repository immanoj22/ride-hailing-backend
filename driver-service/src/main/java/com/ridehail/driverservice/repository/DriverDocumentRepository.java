package com.ridehail.driverservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ridehail.driverservice.entity.DriverDocument;

public interface DriverDocumentRepository extends JpaRepository<DriverDocument, UUID> {

    List<DriverDocument> findByDriver_Id(UUID driverId);

    Optional<DriverDocument> findByDriver_IdAndDocKey(UUID driverId, String docKey);
}
