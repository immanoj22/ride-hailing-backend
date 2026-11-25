package com.ridehail.driverservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ridehail.common.response.ApiResponseService;
import com.ridehail.driverservice.dto.AddWalletTransactionRequest;
import com.ridehail.driverservice.dto.ListWalletClaimsRequest;
import com.ridehail.driverservice.dto.PayWalletClaimRequest;
import com.ridehail.driverservice.dto.RequestWalletClaimRequest;
import com.ridehail.driverservice.dto.ReviewWalletClaimRequest;
import com.ridehail.driverservice.dto.UpdateDriverWalletRequest;
import com.ridehail.driverservice.service.DriverWalletService;

/**
 * Direct port of driverWallet.controller.js: updateWallet, getWallet, addTransaction,
 * getTransaction, requestWalletClaim, listWalletClaims, reviewWalletClaim, payWalletClaim
 * (routes/driver.routes.js, prefix /driver-apis).
 */
@RestController
@RequestMapping("/driver-apis")
public class DriverWalletController {

    private final DriverWalletService driverWalletService;
    private final ApiResponseService apiResponseService;

    public DriverWalletController(DriverWalletService driverWalletService, ApiResponseService apiResponseService) {
        this.driverWalletService = driverWalletService;
        this.apiResponseService = apiResponseService;
    }

    @PostMapping("/update-wallet")
    public ResponseEntity<?> updateWallet(@RequestBody(required = false) UpdateDriverWalletRequest request) {
        return apiResponseService.ok("Driver wallet updated successfully", driverWalletService.updateWallet(request));
    }

    @GetMapping("/get-wallet/{id}")
    public ResponseEntity<?> getWallet(@PathVariable String id) {
        return apiResponseService.ok("Driver wallet fetched successfully", driverWalletService.getWallet(id));
    }

    @PostMapping("/add-transaction")
    public ResponseEntity<?> addTransaction(@RequestBody AddWalletTransactionRequest request) {
        return apiResponseService.ok("Driver wallet transaction created successfully",
                driverWalletService.addTransaction(request));
    }

    @GetMapping("/get-transaction/{id}")
    public ResponseEntity<?> getTransaction(@PathVariable String id) {
        return apiResponseService.ok("Driver wallet transaction fetched successfully",
                driverWalletService.getTransaction(id));
    }

    @PostMapping("/claim-wallet")
    public ResponseEntity<?> requestWalletClaim(@RequestBody(required = false) RequestWalletClaimRequest request) {
        return apiResponseService.ok("Driver wallet claim requested successfully",
                driverWalletService.requestWalletClaim(request));
    }

    @PostMapping("/claim-wallet-list")
    public ResponseEntity<?> listWalletClaims(@RequestBody(required = false) ListWalletClaimsRequest request) {
        return apiResponseService.ok("Wallet claim requests fetched successfully",
                driverWalletService.listWalletClaims(request));
    }

    @PostMapping("/claim-wallet-review")
    public ResponseEntity<?> reviewWalletClaim(@RequestBody(required = false) ReviewWalletClaimRequest request) {
        return apiResponseService.ok("Claim request reviewed successfully",
                driverWalletService.reviewWalletClaim(request));
    }

    @PostMapping("/claim-wallet-pay")
    public ResponseEntity<?> payWalletClaim(@RequestBody(required = false) PayWalletClaimRequest request) {
        return apiResponseService.ok("Claim request paid successfully", driverWalletService.payWalletClaim(request));
    }
}
