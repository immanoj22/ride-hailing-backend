package com.ridehail.driverservice.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

/** Port of driverWallet.controller.js's updateWallet body ({@code driverData} bulk-upsert array). */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateDriverWalletRequest {

    private List<Item> driverData;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private String driverId;
        private BigDecimal currentAmount;
        private BigDecimal claimAmount;
        private BigDecimal pendingAmount;
        private String walletStatus;
    }
}
