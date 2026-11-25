package com.ridehail.driverservice.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.Data;

/** Port of driverWallet.controller.js's addTransaction body / driverwallet.validation.js's addTransactionSchema. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AddWalletTransactionRequest {

    private BigDecimal requestedAmount;
    private String driverId;
    private String transactionType;
    private JsonNode metaData;
}
