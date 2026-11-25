package com.ridehail.driverservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

/** Port of driverWallet.controller.js's listWalletClaims body. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ListWalletClaimsRequest {

    private String status;
    private String driverId;
    private Integer page;
    private Integer limit;
    private Boolean isSummary;
}
