package com.ridehail.rideservice.dto;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Port of ridevalidation.js outstationRideSchema.body - shared by create_outstation_ride and
 * update_outstation_ride. pickup/drop are free-form objects in the Joi schema (Joi.object()).
 */
@Data
public class OutstationRideRequest {

    /** present for update_outstation_ride. */
    private String rideId;

    /** ride.controller.js create branch reads userId off the payload before falling back to req.user. */
    private String userId;

    @NotNull
    private String vehicleTypeId;

    @NotNull
    private String vehicleVariantId;

    @NotNull
    private JsonNode pickup;

    @NotNull
    private JsonNode drop;

    @NotNull
    private String pickupAddress;

    private String dropAddress;

    @NotNull
    private String pickupDistrict;

    private String dropoffDistrict;

    @NotNull
    private String bookingDate;

    private String endDate;

    private String notes = "";

    private Boolean extraCarrier = false;

    private List<Object> multipleDrop;

    private String promo = "";

    /** create branch only - controller rejects rideType != "outstation". */
    private String rideType = "outstation";

    private Boolean isToll = false;

    private String createdBy = "user";

    private Double km;

    private Integer time;

    @NotNull
    @Valid
    private OutstationInfoDto outstation;
}
