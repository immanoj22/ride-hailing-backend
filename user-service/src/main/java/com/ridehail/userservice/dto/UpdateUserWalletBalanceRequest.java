package com.ridehail.userservice.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Body of POST /user-apis/update-wallet-balance, mirrors user.controller.js#update_user_wallet_balance
 * (bulkWrite over {@code walletBalance}, a field distinct from {@code wallet.currentAmount}).
 */
@Getter
@Setter
public class UpdateUserWalletBalanceRequest {

    private List<Adjustment> userData = new ArrayList<>();

    @Getter
    @Setter
    public static class Adjustment {

        private String userId;

        private BigDecimal currentAmount;
    }
}
