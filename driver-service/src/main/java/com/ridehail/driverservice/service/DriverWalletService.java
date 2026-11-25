package com.ridehail.driverservice.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ridehail.common.constants.AuthUserType;
import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.common.security.AuthContext;
import com.ridehail.common.security.InternalServiceClient;
import com.ridehail.driverservice.dto.AddWalletTransactionRequest;
import com.ridehail.driverservice.dto.ListWalletClaimsRequest;
import com.ridehail.driverservice.dto.PayWalletClaimRequest;
import com.ridehail.driverservice.dto.RequestWalletClaimRequest;
import com.ridehail.driverservice.dto.ReviewWalletClaimRequest;
import com.ridehail.driverservice.dto.UpdateDriverWalletRequest;
import com.ridehail.driverservice.entity.Driver;
import com.ridehail.driverservice.entity.DriverWallet;
import com.ridehail.driverservice.entity.DriverWalletTransaction;
import com.ridehail.driverservice.realtime.DriverWalletNotificationPublisher;
import com.ridehail.driverservice.repository.DriverRepository;
import com.ridehail.driverservice.repository.DriverWalletRepository;
import com.ridehail.driverservice.repository.DriverWalletTransactionRepository;

/**
 * Port of driverWallet.controller.js + service/driverwallet.services.js: updateWallet, getWallet,
 * addTransaction, getTransaction, requestWalletClaim, listWalletClaims, reviewWalletClaim,
 * payWalletClaim (routes/driver.routes.js, prefix /driver-apis).
 */
@Service
public class DriverWalletService {

    /** helpers/driver.constants.js's MIN_CLAIM_AMOUNT. */
    private static final BigDecimal MIN_CLAIM_AMOUNT = new BigDecimal("100");

    private final DriverWalletRepository driverWalletRepository;
    private final DriverWalletTransactionRepository driverWalletTransactionRepository;
    private final DriverRepository driverRepository;
    private final InternalServiceClient internalServiceClient;
    private final DriverWalletNotificationPublisher notificationPublisher;
    private final String rideServiceUrl;

    public DriverWalletService(
            DriverWalletRepository driverWalletRepository,
            DriverWalletTransactionRepository driverWalletTransactionRepository,
            DriverRepository driverRepository,
            InternalServiceClient internalServiceClient,
            DriverWalletNotificationPublisher notificationPublisher,
            @Value("${ridehail.services.ride-url}") String rideServiceUrl) {
        this.driverWalletRepository = driverWalletRepository;
        this.driverWalletTransactionRepository = driverWalletTransactionRepository;
        this.driverRepository = driverRepository;
        this.internalServiceClient = internalServiceClient;
        this.notificationPublisher = notificationPublisher;
        this.rideServiceUrl = rideServiceUrl;
    }

    private static UUID parseId(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Invalid " + fieldName);
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Invalid " + fieldName);
        }
    }

    // ---------------------------------------------------------------- updateWallet

    @Transactional
    public Object updateWallet(UpdateDriverWalletRequest request) {
        List<UpdateDriverWalletRequest.Item> driverData = request == null ? null : request.getDriverData();
        if (driverData == null || driverData.isEmpty()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "driverData must be a non-empty array");
        }

        int modified = 0;
        int upserted = 0;
        for (UpdateDriverWalletRequest.Item item : driverData) {
            UUID driverId = parseId(item.getDriverId(), "driverId");
            Driver driverRef = driverRepository.findById(driverId)
                    .orElseThrow(() -> new ApiException(StatusCode.NOT_FOUND, "Driver not found"));

            DriverWallet wallet = driverWalletRepository.findByDriver_Id(driverId).orElse(null);
            boolean isNew = wallet == null;
            if (isNew) {
                wallet = DriverWallet.builder().driver(driverRef).build();
            }

            wallet.setCurrentAmount(nz(wallet.getCurrentAmount()).add(nz(item.getCurrentAmount())));
            wallet.setClaimAmount(nz(wallet.getClaimAmount()).add(nz(item.getClaimAmount())));
            wallet.setPendingAmount(nz(wallet.getPendingAmount()).add(nz(item.getPendingAmount())));
            if (item.getWalletStatus() != null) {
                wallet.setWalletStatus(item.getWalletStatus());
            }
            wallet.setLastClaimDate(OffsetDateTime.now());

            driverWalletRepository.save(wallet);
            if (isNew) {
                upserted++;
            } else {
                modified++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matchedCount", modified);
        result.put("modifiedCount", modified);
        result.put("upsertedCount", upserted);
        return result;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    // ---------------------------------------------------------------- getWallet

    public DriverWallet getWallet(String driverIdRaw) {
        UUID driverId = parseId(driverIdRaw, "ID");
        return driverWalletRepository.findByDriver_Id(driverId)
                .orElseThrow(() -> new ApiException(StatusCode.BAD_REQUEST, "Driver wallet is Empty"));
    }

    // ---------------------------------------------------------------- addTransaction

    @Transactional
    public DriverWalletTransaction addTransaction(AddWalletTransactionRequest request) {
        UUID driverId = parseId(request.getDriverId(), "driverId");
        Driver driverRef = driverRepository.findById(driverId)
                .orElseThrow(() -> new ApiException(StatusCode.NOT_FOUND, "Driver not found"));

        DriverWalletTransaction transaction = DriverWalletTransaction.builder()
                .driver(driverRef)
                .requestedAmount(request.getRequestedAmount() == null ? BigDecimal.ZERO : request.getRequestedAmount())
                .requestedDate(OffsetDateTime.now())
                .transactionType(request.getTransactionType())
                .metaData(request.getMetaData() == null ? JsonNodeFactory.instance.objectNode() : request.getMetaData())
                .build();

        return driverWalletTransactionRepository.save(transaction);
    }

    // ---------------------------------------------------------------- getTransaction

    public List<DriverWalletTransaction> getTransaction(String driverIdRaw) {
        UUID driverId = parseId(driverIdRaw, "ID");
        return driverWalletTransactionRepository.findByDriver_Id(driverId);
    }

    // ---------------------------------------------------------------- requestWalletClaim

    @Transactional
    public Map<String, Object> requestWalletClaim(RequestWalletClaimRequest request) {
        AuthContext ctx = AuthContext.current();
        AuthUserType authUserType = ctx == null ? null : ctx.authUserType();
        if (authUserType != AuthUserType.DRIVER && authUserType != AuthUserType.IN_APP) {
            throw new ApiException(StatusCode.FORBIDDEN, "Only drivers can request wallet claims");
        }

        String resolvedDriverIdRaw = request != null && request.getDriverId() != null
                ? request.getDriverId()
                : (ctx == null ? null : ctx.principalId());
        UUID driverId = parseId(resolvedDriverIdRaw, "driverId");

        DriverWallet wallet = driverWalletRepository.findByDriver_Id(driverId)
                .orElseThrow(() -> new ApiException(StatusCode.BAD_REQUEST, "No claimable amount in wallet"));

        if (nz(wallet.getCurrentAmount()).compareTo(MIN_CLAIM_AMOUNT) <= 0) {
            throw new ApiException(StatusCode.BAD_REQUEST, "No claimable amount in wallet");
        }

        driverWalletTransactionRepository.findByDriver_IdAndTransactionTypeAndStatus(driverId, "claim", "pending")
                .ifPresent(existing -> {
                    throw new ApiException(StatusCode.CONFLICT, "A claim request is already pending");
                });

        BigDecimal claimableAmount = nz(wallet.getCurrentAmount());

        DriverWalletTransaction claimTransaction = DriverWalletTransaction.builder()
                .driver(wallet.getDriver())
                .requestedAmount(claimableAmount)
                .requestedDate(OffsetDateTime.now())
                .transactionType("claim")
                .status("pending")
                .metaData(JsonNodeFactory.instance.objectNode())
                .build();
        claimTransaction = driverWalletTransactionRepository.save(claimTransaction);

        wallet.setPendingAmount(claimableAmount);
        wallet.setCurrentAmount(BigDecimal.ZERO);
        wallet = driverWalletRepository.save(wallet);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("claimTransaction", claimTransaction);
        result.put("wallet", wallet);
        return result;
    }

    // ---------------------------------------------------------------- listWalletClaims

    public Map<String, Object> listWalletClaims(ListWalletClaimsRequest request) {
        AuthContext ctx = AuthContext.current();
        AuthUserType authUserType = ctx == null ? null : ctx.authUserType();
        if (authUserType != AuthUserType.ADMIN && authUserType != AuthUserType.IN_APP
                && authUserType != AuthUserType.DRIVER) {
            throw new ApiException(StatusCode.FORBIDDEN, "Only admins can view claim requests");
        }

        String status = request == null ? null : request.getStatus();
        String driverIdRaw = request == null ? null : request.getDriverId();
        int page = request != null && request.getPage() != null && request.getPage() > 0 ? request.getPage() : 1;
        int limit = request != null && request.getLimit() != null && request.getLimit() > 0 ? request.getLimit() : 10;

        UUID driverId = null;
        if (driverIdRaw != null && !driverIdRaw.isBlank()) {
            try {
                driverId = UUID.fromString(driverIdRaw);
            } catch (IllegalArgumentException ignored) {
                // Mirrors the original: an invalid driverId is silently dropped from the filter.
                driverId = null;
            }
        }

        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt"));

        List<DriverWalletTransaction> claims;
        long total;
        if (driverId != null) {
            if (status != null && !status.isBlank()) {
                var claimsPage = driverWalletTransactionRepository
                        .findByTransactionTypeAndStatusAndDriver_Id("claim", status, driverId, pageable);
                claims = claimsPage.getContent();
                total = claimsPage.getTotalElements();
            } else {
                var claimsPage = driverWalletTransactionRepository
                        .findByTransactionTypeAndDriver_Id("claim", driverId, pageable);
                claims = claimsPage.getContent();
                total = claimsPage.getTotalElements();
            }
        } else if (status != null && !status.isBlank()) {
            var claimsPage = driverWalletTransactionRepository.findByTransactionTypeAndStatus("claim", status, pageable);
            claims = claimsPage.getContent();
            total = claimsPage.getTotalElements();
        } else {
            var claimsPage = driverWalletTransactionRepository.findByTransactionType("claim", pageable);
            claims = claimsPage.getContent();
            total = claimsPage.getTotalElements();
        }

        List<Map<String, Object>> claimsWithDrivers;
        Map<String, Object> summaryData = new LinkedHashMap<>();

        if (driverId == null) {
            Set<UUID> driverIds = claims.stream()
                    .map(c -> c.getDriver() == null ? null : c.getDriver().getId())
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());

            Map<UUID, Driver> driverMap = driverIds.isEmpty()
                    ? Map.of()
                    : driverRepository.findByIdIn(new ArrayList<>(driverIds)).stream()
                            .collect(Collectors.toMap(Driver::getId, d -> d));

            claimsWithDrivers = claims.stream().map(c -> {
                Map<String, Object> row = toClaimMap(c);
                Driver d = c.getDriver() == null ? null : driverMap.get(c.getDriver().getId());
                row.put("driver", d == null ? null : driverSummary(d));
                return row;
            }).toList();

            List<Object[]> summaryAgg = driverWalletTransactionRepository.aggregateByStatus(
                    "claim", (status != null && !status.isBlank()) ? status : null);

            Map<String, Object> base = new LinkedHashMap<>();
            base.put("totalRequests", total);
            base.put("totalRequestedAmount", BigDecimal.ZERO);
            base.put("totalPaidAmount", BigDecimal.ZERO);
            base.put("pending", newCounter());
            base.put("approved", newCounter());
            base.put("rejected", newCounter());
            base.put("paid", newPaidCounter());

            BigDecimal totalRequestedAmount = BigDecimal.ZERO;
            BigDecimal totalPaidAmount = BigDecimal.ZERO;
            for (Object[] row : summaryAgg) {
                String key = (String) row[0];
                long count = ((Number) row[1]).longValue();
                BigDecimal requestedAmount = (BigDecimal) row[2];
                BigDecimal paidAmount = (BigDecimal) row[3];

                @SuppressWarnings("unchecked")
                Map<String, Object> bucket = (Map<String, Object>) base.get(key);
                if (bucket != null) {
                    bucket.put("count", count);
                    bucket.put("amount", requestedAmount);
                    if ("paid".equals(key)) {
                        bucket.put("paidAmount", paidAmount);
                    }
                }
                totalRequestedAmount = totalRequestedAmount.add(requestedAmount);
                totalPaidAmount = totalPaidAmount.add(paidAmount);
            }
            base.put("totalRequestedAmount", totalRequestedAmount);
            base.put("totalPaidAmount", totalPaidAmount);
            summaryData = base;
        } else {
            claimsWithDrivers = claims.stream().map(DriverWalletService::toClaimMap).toList();
        }

        Map<String, Object> pagination = new LinkedHashMap<>();
        pagination.put("page", page);
        pagination.put("limit", limit);
        pagination.put("total", total);
        pagination.put("totalPages", (int) Math.ceil((double) total / limit));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("claims", claimsWithDrivers);
        data.put("summary", summaryData);
        data.put("pagination", pagination);
        return data;
    }

    private static Map<String, Object> newCounter() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("count", 0L);
        m.put("amount", BigDecimal.ZERO);
        return m;
    }

    private static Map<String, Object> newPaidCounter() {
        Map<String, Object> m = newCounter();
        m.put("paidAmount", BigDecimal.ZERO);
        return m;
    }

    private static Map<String, Object> toClaimMap(DriverWalletTransaction t) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", t.getId());
        row.put("driverId", t.getDriver() == null ? null : t.getDriver().getId());
        row.put("requestedAmount", t.getRequestedAmount());
        row.put("requestedDate", t.getRequestedDate());
        row.put("status", t.getStatus());
        row.put("transactionType", t.getTransactionType());
        row.put("metaData", t.getMetaData());
        row.put("reviewedBy", t.getReviewedBy());
        row.put("reviewedAt", t.getReviewedAt());
        row.put("paidBy", t.getPaidBy());
        row.put("paidAt", t.getPaidAt());
        row.put("paidAmount", t.getPaidAmount());
        row.put("note", t.getNote());
        row.put("createdAt", t.getCreatedAt());
        row.put("updatedAt", t.getUpdatedAt());
        return row;
    }

    private static Map<String, Object> driverSummary(Driver d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("firstName", d.getFirstName());
        m.put("lastName", d.getLastName());
        m.put("mobile", d.getMobile());
        m.put("email", d.getEmail());
        Map<String, Object> bankDetails = new LinkedHashMap<>();
        bankDetails.put("holderName", d.getBankHolderName());
        bankDetails.put("accountNumber", d.getBankAccountNumber());
        bankDetails.put("ifsc", d.getBankIfsc());
        bankDetails.put("bankName", d.getBankName());
        bankDetails.put("branch", d.getBankBranch());
        bankDetails.put("upiId", d.getBankUpiId());
        m.put("bankDetails", bankDetails);
        return m;
    }

    // ---------------------------------------------------------------- reviewWalletClaim

    @Transactional
    public DriverWalletTransaction reviewWalletClaim(ReviewWalletClaimRequest request) {
        AuthContext ctx = AuthContext.current();
        AuthUserType authUserType = ctx == null ? null : ctx.authUserType();
        if (authUserType != AuthUserType.ADMIN && authUserType != AuthUserType.IN_APP) {
            throw new ApiException(StatusCode.FORBIDDEN, "Only admins can review claim requests");
        }

        UUID transactionId = parseId(request == null ? null : request.getTransactionId(), "transactionId");
        String status = request == null ? null : request.getStatus();
        if (!"approved".equals(status) && !"rejected".equals(status)) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Status must be approved or rejected");
        }

        DriverWalletTransaction claim = driverWalletTransactionRepository.findById(transactionId)
                .filter(t -> "claim".equals(t.getTransactionType()) && "pending".equals(t.getStatus()))
                .orElseThrow(() -> new ApiException(StatusCode.CONFLICT, "Claim request is already processed or not found"));

        UUID adminId = resolveAdminId(ctx);

        claim.setStatus(status);
        claim.setReviewedAt(OffsetDateTime.now());
        claim.setReviewedBy(adminId);
        claim.setNote(request.getNote() == null ? "" : request.getNote());
        claim = driverWalletTransactionRepository.save(claim);

        boolean isApproved = "approved".equals(status);
        BigDecimal rejectedAmount = nz(claim.getRequestedAmount());

        if (!isApproved && rejectedAmount.compareTo(BigDecimal.ZERO) > 0) {
            UUID driverId = claim.getDriver().getId();
            DriverWallet wallet = driverWalletRepository.findByDriver_Id(driverId).orElse(null);
            if (wallet != null) {
                wallet.setCurrentAmount(nz(wallet.getCurrentAmount()).add(rejectedAmount));
                wallet.setPendingAmount(nz(wallet.getPendingAmount()).subtract(rejectedAmount));
                driverWalletRepository.save(wallet);
            }
        }

        String note = claim.getNote();
        notificationPublisher.notifyDriverClaimUpdate(
                claim.getDriver().getId(),
                isApproved ? "claimapproved" : "claimrejected",
                isApproved ? "Claim Approved" : "Claim Request Rejected",
                isApproved
                        ? "Your wallet claim request for Rs." + rejectedAmount + " has been approved. Payment will be processed soon."
                        : (note != null && !note.isBlank()
                                ? "Your wallet claim request was rejected. Reason: " + note
                                : "Your wallet claim request was rejected."),
                claim.getId());

        return claim;
    }

    // ---------------------------------------------------------------- payWalletClaim

    @Transactional
    public Map<String, Object> payWalletClaim(PayWalletClaimRequest request) {
        AuthContext ctx = AuthContext.current();
        AuthUserType authUserType = ctx == null ? null : ctx.authUserType();
        if (authUserType != AuthUserType.ADMIN && authUserType != AuthUserType.IN_APP) {
            throw new ApiException(StatusCode.FORBIDDEN, "Only admins can pay claim requests");
        }

        UUID transactionId = parseId(request == null ? null : request.getTransactionId(), "transactionId");

        DriverWalletTransaction claim = driverWalletTransactionRepository.findById(transactionId)
                .filter(t -> "claim".equals(t.getTransactionType()))
                .orElseThrow(() -> new ApiException(StatusCode.NOT_FOUND, "Claim request not found"));

        if ("paid".equals(claim.getStatus())) {
            throw new ApiException(StatusCode.CONFLICT, "Claim request is already paid");
        }
        if (!"approved".equals(claim.getStatus())) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Claim request must be approved before payment");
        }

        UUID driverId = claim.getDriver().getId();
        DriverWallet wallet = driverWalletRepository.findByDriver_Id(driverId)
                .orElseThrow(() -> new ApiException(StatusCode.NOT_FOUND, "Driver wallet not found"));

        BigDecimal payoutAmount = nz(wallet.getPendingAmount());

        JsonNode claimResp = internalServiceClient.post(
                rideServiceUrl + "/driver-ride-claim", Map.of("driverId", driverId.toString()));
        if (!internalServiceClient.statusTrue(claimResp)) {
            String message = claimResp != null && claimResp.hasNonNull("message")
                    ? claimResp.get("message").asText()
                    : "Failed to mark ride payments as claimed";
            throw new ApiException(StatusCode.INTERNAL_SERVER_ERROR, message);
        }

        wallet.setPendingAmount(BigDecimal.ZERO);
        wallet.setLastClaimDate(OffsetDateTime.now());
        wallet.setClaimAmount(nz(wallet.getClaimAmount()).add(payoutAmount));
        wallet = driverWalletRepository.save(wallet);

        UUID adminId = resolveAdminId(ctx);

        claim.setStatus("paid");
        claim.setPaidAt(OffsetDateTime.now());
        claim.setPaidBy(adminId);
        claim.setPaidAmount(payoutAmount);
        claim = driverWalletTransactionRepository.save(claim);

        notificationPublisher.notifyDriverClaimUpdate(
                driverId,
                "claimpaid",
                "Claim Paid",
                "Your wallet claim of Rs." + payoutAmount + " has been paid successfully.",
                claim.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("transaction", claim);
        result.put("wallet", wallet);
        result.put("ridePayments", claimResp.path("data"));
        return result;
    }

    private static UUID resolveAdminId(AuthContext ctx) {
        if (ctx == null || ctx.principalId() == null) {
            return null;
        }
        try {
            return UUID.fromString(ctx.principalId());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
