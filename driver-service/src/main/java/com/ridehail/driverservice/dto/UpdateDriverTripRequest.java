package com.ridehail.driverservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Equivalent of driver.controller.js's update_driver_trip payload ({@code {driverId, tripId,
 * status}}), posted both over HTTP (routes/driver.routes.js's POST /update-driver-trip) and
 * internally from helpers/driver.subscriber.js.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateDriverTripRequest {

    private String driverId;
    private String tripId;
    private String status;

    public String getDriverId() {
        return driverId;
    }

    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }

    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
