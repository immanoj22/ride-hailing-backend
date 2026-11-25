package com.ridehail.rideservice.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ridehail.rideservice.entity.Ride;

/**
 * Mirrors the many Ride.find/findOne query shapes built dynamically in ride.service.js/
 * ride.controller.js (get_ride, get_all_ride, get_ongoing_rides_admin, rides-summary,
 * driver/:driverId/rides, active/pending-by-driver, get-latest-ride-user, get-rides-by-user, etc).
 * Most of those endpoints build ad-hoc filter/sort combinations from query params, so this also
 * extends JpaSpecificationExecutor for the implementation step to compose those dynamically instead
 * of exploding into dozens of derived-query method combinations.
 */
public interface RideRepository extends JpaRepository<Ride, UUID>, JpaSpecificationExecutor<Ride> {

    Optional<Ride> findByBookingNumber(String bookingNumber);

    Optional<Ride> findByIdAndDeletedFalse(UUID id);

    Optional<Ride> findByIdAndUserIdAndRideTypeAndStatusAndDeletedFalseAndDriverIdIsNull(
            UUID id, UUID userId, String rideType, String status);

    Page<Ride> findByUserId(UUID userId, Pageable pageable);

    Page<Ride> findByDriverId(UUID driverId, Pageable pageable);

    List<Ride> findByDriverIdAndStatusIn(UUID driverId, List<String> statuses);

    Optional<Ride> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    Page<Ride> findByRideType(String rideType, Pageable pageable);

    /**
     * ride.service.js getPendingRideAmounts: SUM(cancelAmount) over the user's driver-assigned
     * cancelled rides that are still unpaid. Used by calculateFinalLocalFare / calculateFinalOutstationFare.
     */
    @Query("""
            select coalesce(sum(r.cancelAmount), 0) from Ride r
            where r.userId = :userId
              and r.driverId is not null
              and r.status in ('driver_cancel', 'user_cancel')
              and r.canceledBy in ('driver', 'user')
              and r.cancelAmountPaid = false
            """)
    BigDecimal sumPendingCancelAmounts(@Param("userId") UUID userId);

    /** ride_completePayment: mark every unpaid user/driver-cancelled ride as paid. */
    @Query("""
            update Ride r set r.cancelAmountPaid = true
            where r.userId = :userId
              and r.driverId is not null
              and r.cancelAmountPaid = false
              and r.status in ('driver_cancel', 'user_cancel')
              and r.canceledBy in ('driver', 'user')
            """)
    @org.springframework.data.jpa.repository.Modifying
    int markCancelAmountsPaid(@Param("userId") UUID userId);

    List<Ride> findByUserIdAndStatusAndRideType(UUID userId, String status, String rideType);

    long countByCreatedAtBetween(OffsetDateTime from, OffsetDateTime to);

    List<Ride> findByDriverIdAndStatusInOrderByCreatedAtDesc(UUID driverId, List<String> statuses);

    List<Ride> findByDriverIdOrderByCreatedAtDesc(UUID driverId);

    /** get_pending_ride_by_driver: status in (create,updated), requestedDrivers contains driver, no driver assigned. */
    @Query(value = """
            select * from rides r
            where r.status in ('create','updated')
              and r.driver_id is null
              and :driverId = any(r.requested_drivers)
            limit 1
            """, nativeQuery = true)
    Optional<Ride> findPendingByRequestedDriver(@Param("driverId") UUID driverId);

    Optional<Ride> findFirstByDriverIdAndStatusInOrderByCreatedAtDesc(UUID driverId, List<String> statuses);

    /** get_ride_activity_chart series: rides in the window (delete:false, createdAt between). */
    List<Ride> findByDeletedFalseAndCreatedAtBetween(OffsetDateTime from, OffsetDateTime to);

    /** get_ride_activity_chart summary counters. */
    long countByDeletedFalseAndCreatedAtGreaterThanEqual(OffsetDateTime from);

    long countByDeletedFalseAndStatusInAndCreatedAtGreaterThanEqual(List<String> statuses, OffsetDateTime from);
}
