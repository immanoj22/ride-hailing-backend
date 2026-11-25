package com.ridehail.userservice.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ridehail.common.response.ApiResponseService;
import com.ridehail.userservice.dto.AddRefererRequest;
import com.ridehail.userservice.dto.BulkWalletUpdateResult;
import com.ridehail.userservice.dto.ConsumeReferralDiscountRequest;
import com.ridehail.userservice.dto.ConsumeReferralDiscountResponse;
import com.ridehail.userservice.dto.ReferralEarningsResponse;
import com.ridehail.userservice.dto.UpdateUserWalletBalanceRequest;
import com.ridehail.userservice.dto.UpdateWalletRequest;
import com.ridehail.userservice.dto.UserResponse;
import com.ridehail.userservice.service.WalletReferralService;

import jakarta.validation.Valid;

/**
 * Direct port of the "wallet-referral-rewards" bucket of controller/user.controller.js:
 * add_referer, update_wallet, consume_referral_discount, update_user_wallet_balance,
 * get_referral_earnings. Base path matches server.js's {@code app.use("/user-apis", routes)}.
 */
@RestController
@RequestMapping("/user-apis")
public class WalletReferralController {

    private final WalletReferralService walletReferralService;
    private final ApiResponseService apiResponseService;

    public WalletReferralController(WalletReferralService walletReferralService, ApiResponseService apiResponseService) {
        this.walletReferralService = walletReferralService;
        this.apiResponseService = apiResponseService;
    }

    @PatchMapping("/add-refferal")
    public ResponseEntity<?> addReferer(@Valid @RequestBody AddRefererRequest request) {
        walletReferralService.addReferer(request);
        return apiResponseService.ok("Reffered successfully", Map.of());
    }

    @PostMapping("/update-wallet")
    public ResponseEntity<?> updateWallet(@RequestBody UpdateWalletRequest request) {
        UserResponse user = walletReferralService.updateWallet(request);
        return apiResponseService.ok("Wallet updated successfully", Map.of("user", user));
    }

    @PostMapping("/consume-referral-discount")
    public ResponseEntity<?> consumeReferralDiscount(@RequestBody ConsumeReferralDiscountRequest request) {
        ConsumeReferralDiscountResponse response = walletReferralService.consumeReferralDiscount(request);
        return apiResponseService.ok("Referral discount checked", response);
    }

    @PostMapping("/update-wallet-balance")
    public ResponseEntity<?> updateUserWalletBalance(@RequestBody UpdateUserWalletBalanceRequest request) {
        BulkWalletUpdateResult result = walletReferralService.updateUserWalletBalance(request);
        return apiResponseService.ok("User wallet balance updated successfully", result);
    }

    @GetMapping("/referral-earnings")
    public ResponseEntity<?> getReferralEarnings(@RequestParam(value = "userId", required = false) String userId) {
        ReferralEarningsResponse response = walletReferralService.getReferralEarnings(userId);
        return apiResponseService.ok("Referral earnings fetched successfully", response);
    }
}
