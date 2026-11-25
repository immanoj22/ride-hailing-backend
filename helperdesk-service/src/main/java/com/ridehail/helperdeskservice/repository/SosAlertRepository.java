package com.ridehail.helperdeskservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ridehail.helperdeskservice.entity.SosAlert;

/** Mirrors sosSave/findOneSOS/findSOS/getSOSCount in helperdesk.services.js. */
public interface SosAlertRepository extends JpaRepository<SosAlert, UUID> {

    /** triggerSOS's "already an active SOS for this ride" existence check. */
    Optional<SosAlert> findByRideIdAndUserIdAndUserType(UUID rideId, UUID userId, String userType);

    /** listSOS filtered by userType (query.userType) when no explicit sosId is given. */
    Page<SosAlert> findByUserType(String userType, Pageable pageable);
}
