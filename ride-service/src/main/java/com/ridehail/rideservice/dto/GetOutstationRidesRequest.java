package com.ridehail.rideservice.dto;

import lombok.Data;

/**
 * Port of ridevalidation.js getOutstationRidesSchema.body (also reused loosely by
 * get_outstation_ride_list, which reads extra optional fields off req.body). The Joi ".xor" rule
 * (exactly one of userId / driverId) is enforced in the service.
 */
@Data
public class GetOutstationRidesRequest {

    private String userId;

    private String driverId;

    private String status;

    private Boolean onTripRoute = false;

    private Integer page = 1;

    private Integer limit = 10;

    private Boolean getCount = false;

    private String search;

    private String tripType;

    private String payment;
}
