package com.ridehail.rideservice.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** validations/vehiclevalidation.js addPriceSchema. */
@Data
public class AddPriceRequest {

    private String vehicleTypeId;
    private String vehicleVariantId;

    @NotNull @DecimalMin("0") private BigDecimal pricePerKm;
    @NotNull @DecimalMin("0") private BigDecimal pricePerMin;
    @NotNull @DecimalMin("0") private BigDecimal priceSurge;
    @NotNull @DecimalMin("0") private BigDecimal basePrice;
    @NotNull @DecimalMin("0") private BigDecimal pricePerDay;

    @NotNull @DecimalMin("0") private BigDecimal insurance;
    @NotNull @DecimalMin("0") private BigDecimal tollTax;
    @NotNull @DecimalMin("0") private BigDecimal mcdTax;
    @NotNull @DecimalMin("0") private BigDecimal gstPercent;
    @NotNull @DecimalMin("0") private BigDecimal commissionPercent;

    @NotNull @DecimalMin("0") private BigDecimal peakTimeCharge;

    @NotNull @DecimalMin("0") private BigDecimal userCancellationTimeLimitMinutes;
    @NotNull @DecimalMin("0") private BigDecimal userCancellationCharge;
    @NotNull @DecimalMin("0") private BigDecimal waitingTimeLimitMinutes;
    @NotNull @DecimalMin("0") private BigDecimal waitingChargePerMin;
    @NotNull @DecimalMin("0") private BigDecimal transitWaitingFeePerMin;

    @NotNull private BigDecimal others1;
    @NotNull private BigDecimal others2;
    @NotNull private BigDecimal others3;

    @NotNull
    private List<String> paymentModeSetting;

    private JsonNode outstation;

    @NotNull
    @Pattern(regexp = "local|outstation")
    private String category;
}
