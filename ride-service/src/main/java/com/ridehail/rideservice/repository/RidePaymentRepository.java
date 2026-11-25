package com.ridehail.rideservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.ridehail.rideservice.entity.RidePayment;

/**
 * Mirrors modals/ridePaymentSchema.js - getDriverRidePayments/claimDriverRidePayments/
 * getDriverReferralEarnings/getReferralDriverRidePayments in ride.controller.js.
 */
public interface RidePaymentRepository extends JpaRepository<RidePayment, UUID>,
        JpaSpecificationExecutor<RidePayment> {

    List<RidePayment> findByRideId(UUID rideId);

    List<RidePayment> findByPaymentUserIdAndAction(UUID paymentUserId, String action);

    List<RidePayment> findByPaymentUserIdAndActionAndClaimed(UUID paymentUserId, String action, boolean claimed);

    Optional<RidePayment> findByRideIdAndActionAndPaymentUserId(UUID rideId, String action, UUID paymentUserId);

    /** claimDriverRidePayments: bulk mark unclaimed driver fee/referral payments as claimed. Returns modified count. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("""
            update RidePayment p set p.claimed = true, p.updatedAt = current_timestamp
            where p.paymentUserId = :driverId
              and p.action in ('driverreferralfees', 'driverfees')
              and p.paymentTo = 'driver'
              and p.claimed = false
            """)
    int markDriverPaymentsClaimed(@org.springframework.data.repository.query.Param("driverId") UUID driverId);

    /** getDriverReferralEarnings aggregation: [0]=total, [1]=pendingAmount, [2]=pendingCount. */
    @org.springframework.data.jpa.repository.Query("""
            select coalesce(sum(p.amount), 0),
                   coalesce(sum(case when p.claimed = false then p.amount else 0 end), 0),
                   coalesce(sum(case when p.claimed = false then 1 else 0 end), 0)
            from RidePayment p
            where p.paymentUserId = :driverId
              and p.action = 'driverreferralfees'
              and p.paymentTo = 'driver'
            """)
    Object[] driverReferralEarnings(@org.springframework.data.repository.query.Param("driverId") UUID driverId);

    /** getReferralDriverRidePayments summary: [0]=totalEarnings, [1]=pendingEarnings. */
    @org.springframework.data.jpa.repository.Query("""
            select coalesce(sum(p.amount), 0),
                   coalesce(sum(case when p.claimed = false then p.amount else 0 end), 0)
            from RidePayment p
            where p.paymentUserId = :ownerDriverId
              and p.action = 'driverreferralfees'
              and p.paymentTo = 'driver'
              and p.rideId in :rideIds
            """)
    Object[] referralDriverPaymentsSummary(@org.springframework.data.repository.query.Param("ownerDriverId") UUID ownerDriverId,
            @org.springframework.data.repository.query.Param("rideIds") List<UUID> rideIds);

    List<RidePayment> findByPaymentUserIdAndActionAndRideIdInOrderByCreatedAtDesc(
            UUID paymentUserId, String action, List<UUID> rideIds);
}
