package com.ridehail.rideservice.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.rideservice.entity.Ride;
import com.ridehail.rideservice.entity.RidePayment;
import com.ridehail.rideservice.repository.RidePaymentRepository;
import com.ridehail.rideservice.repository.RideRepository;

/**
 * Ports ride.controller.js: getDriverRidePayments, claimDriverRidePayments,
 * getDriverReferralEarnings, getReferralDriverRidePayments (routes/ride.routes.js prefix /ride-apis).
 */
@Service
@Transactional(readOnly = true)
public class RidePaymentsService {

    private static final List<String> DRIVER_FEE_ACTIONS = List.of("driverreferralfees", "driverfees");

    private final RidePaymentRepository ridePaymentRepository;
    private final RideRepository rideRepository;

    public RidePaymentsService(RidePaymentRepository ridePaymentRepository, RideRepository rideRepository) {
        this.ridePaymentRepository = ridePaymentRepository;
        this.rideRepository = rideRepository;
    }

    private static UUID toUuid(String v, String message) {
        try {
            return UUID.fromString(v);
        } catch (Exception e) {
            throw new ApiException(StatusCode.BAD_REQUEST, message);
        }
    }

    public Map<String, Object> getDriverRidePayments(String driverId, Boolean isClaimed, Integer pageIn, Integer limitIn) {
        int page = pageIn != null && pageIn > 0 ? pageIn : 1;
        int limit = limitIn != null && limitIn > 0 ? limitIn : 10;

        if (driverId == null || driverId.isBlank()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Valid driverId is required");
        }
        UUID driverUuid = toUuid(driverId, "Valid driverId is required");

        final boolean filterClaimed = isClaimed != null;
        Specification<RidePayment> spec = (root, q, cb) -> {
            var preds = new ArrayList<jakarta.persistence.criteria.Predicate>();
            preds.add(cb.equal(root.get("paymentUserId"), driverUuid));
            preds.add(cb.equal(root.get("paymentTo"), "driver"));
            if (filterClaimed) {
                preds.add(cb.equal(root.get("action"), "driverreferralfees"));
                preds.add(cb.equal(root.get("claimed"), isClaimed));
            } else {
                preds.add(root.get("action").in(DRIVER_FEE_ACTIONS));
            }
            return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        long total = ridePaymentRepository.count(spec);
        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<RidePayment> payments = ridePaymentRepository.findAll(spec, pageable).getContent();

        Map<UUID, Ride> rideMap = loadRides(payments.stream().map(RidePayment::getRideId).toList());

        List<Map<String, Object>> paymentDtos = payments.stream()
                .map(p -> paymentDto(p, rideMap.get(p.getRideId()))).toList();

        Map<String, Object> pagination = new LinkedHashMap<>();
        pagination.put("page", page);
        pagination.put("limit", limit);
        pagination.put("total", total);
        pagination.put("totalPages", (int) Math.ceil((double) total / limit));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("payments", paymentDtos);
        data.put("pagination", pagination);
        return data;
    }

    @Transactional
    public Map<String, Object> claimDriverRidePayments(String driverId) {
        if (driverId == null || driverId.isBlank()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Valid driverId is required");
        }
        UUID driverUuid = toUuid(driverId, "Valid driverId is required");
        int modified = ridePaymentRepository.markDriverPaymentsClaimed(driverUuid);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("matched", modified);
        data.put("modified", modified);
        return data;
    }

    public Map<String, Object> getDriverReferralEarnings(String driverId) {
        if (driverId == null || driverId.isBlank()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Valid driverId is required");
        }
        UUID driverUuid = toUuid(driverId, "Valid driverId is required");
        Object[] row = ridePaymentRepository.driverReferralEarnings(driverUuid);
        Object[] agg = (row != null && row.length == 1 && row[0] instanceof Object[] inner) ? inner : row;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalReferralEarnings", num(agg, 0));
        summary.put("pendingReferralEarnings", num(agg, 1));
        summary.put("pendingReferrals", intVal(agg, 2));
        return summary;
    }

    public Map<String, Object> getReferralDriverRidePayments(String refDriverId, String ownerDriverId) {
        if (refDriverId == null || refDriverId.isBlank()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Valid refDriverId is required");
        }
        UUID refDriverUuid = toUuid(refDriverId, "Valid refDriverId is required");
        UUID ownerDriverUuid = ownerDriverId == null ? null : toUuid(ownerDriverId, "Valid ownerDriverId is required");

        List<UUID> rideIds = rideRepository
                .findByDriverIdAndStatusIn(refDriverUuid, List.of("completepayment", "endtrip"))
                .stream().map(Ride::getId).toList();

        BigDecimal totalEarnings = BigDecimal.ZERO;
        BigDecimal pendingEarnings = BigDecimal.ZERO;
        List<Map<String, Object>> rides = new ArrayList<>();

        if (!rideIds.isEmpty() && ownerDriverUuid != null) {
            Object[] row = ridePaymentRepository.referralDriverPaymentsSummary(ownerDriverUuid, rideIds);
            Object[] agg = (row != null && row.length == 1 && row[0] instanceof Object[] inner) ? inner : row;
            totalEarnings = num(agg, 0);
            pendingEarnings = num(agg, 1);

            List<RidePayment> payments = ridePaymentRepository
                    .findByPaymentUserIdAndActionAndRideIdInOrderByCreatedAtDesc(ownerDriverUuid, "driverreferralfees", rideIds);
            for (RidePayment p : payments) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("rideId", p.getRideId());
                r.put("amount", p.getAmount() == null ? 0 : p.getAmount());
                r.put("date", p.getCreatedAt());
                rides.add(r);
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalEarnings", totalEarnings);
        summary.put("pendingEarnings", pendingEarnings);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summary", summary);
        data.put("rides", rides);
        return data;
    }

    private Map<UUID, Ride> loadRides(List<UUID> ids) {
        List<UUID> distinct = ids.stream().filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return rideRepository.findAllById(distinct).stream().collect(Collectors.toMap(Ride::getId, r -> r));
    }

    private Map<String, Object> paymentDto(RidePayment p, Ride ride) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", p.getId());
        m.put("action", p.getAction());
        m.put("paymentTo", p.getPaymentTo());
        m.put("amount", p.getAmount());
        m.put("isClaimed", p.isClaimed());
        m.put("paymentUserId", p.getPaymentUserId());
        m.put("createdAt", p.getCreatedAt());
        m.put("updatedAt", p.getUpdatedAt());
        if (ride == null) {
            m.put("rideId", p.getRideId());
        } else {
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("_id", ride.getId());
            rm.put("bookingNumber", ride.getBookingNumber());
            rm.put("rideType", ride.getRideType());
            rm.put("status", ride.getStatus());
            rm.put("paymentMethod", ride.getPaymentMethod());
            rm.put("updatedAt", ride.getUpdatedAt());
            m.put("rideId", rm);
        }
        return m;
    }

    private static BigDecimal num(Object[] agg, int i) {
        if (agg == null || i >= agg.length || agg[i] == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(agg[i].toString());
    }

    private static long intVal(Object[] agg, int i) {
        if (agg == null || i >= agg.length || agg[i] == null) {
            return 0L;
        }
        return Long.parseLong(agg[i].toString().split("\\.")[0]);
    }
}
