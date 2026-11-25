package com.ridehail.driverservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NearbyDriversRequest {

    private String _id;
    private LatLngRequest pickup;
    private String vehicleTypeId;
    private String vehicleVariantId;
    private Double radiusMeters = 5000d;
    /** OutstationRideService.nearbyDriversAvailable (ride-service) sends "maxDistance" instead of
     *  "radiusMeters" for this same endpoint - accept either. */
    private Double maxDistance;
    private Integer limit = 10;

    public String get_id() {
        return _id;
    }

    public void set_id(String _id) {
        this._id = _id;
    }

    public LatLngRequest getPickup() {
        return pickup;
    }

    public void setPickup(LatLngRequest pickup) {
        this.pickup = pickup;
    }

    public String getVehicleTypeId() {
        return vehicleTypeId;
    }

    public void setVehicleTypeId(String vehicleTypeId) {
        this.vehicleTypeId = vehicleTypeId;
    }

    public String getVehicleVariantId() {
        return vehicleVariantId;
    }

    public void setVehicleVariantId(String vehicleVariantId) {
        this.vehicleVariantId = vehicleVariantId;
    }

    public Double getRadiusMeters() {
        return radiusMeters;
    }

    public void setRadiusMeters(Double radiusMeters) {
        this.radiusMeters = radiusMeters;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public Double getMaxDistance() {
        return maxDistance;
    }

    public void setMaxDistance(Double maxDistance) {
        this.maxDistance = maxDistance;
    }
}
