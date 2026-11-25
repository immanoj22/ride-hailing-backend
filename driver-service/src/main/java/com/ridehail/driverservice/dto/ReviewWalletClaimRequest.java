package com.ridehail.driverservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

/** Port of driverWallet.controller.js's reviewWalletClaim body. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReviewWalletClaimRequest {

    private String transactionId;
    private String status;
    private String note;
}
