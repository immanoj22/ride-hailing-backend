package com.ridehail.driverservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

/** Port of driverWallet.controller.js's payWalletClaim body. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PayWalletClaimRequest {

    private String transactionId;
}
