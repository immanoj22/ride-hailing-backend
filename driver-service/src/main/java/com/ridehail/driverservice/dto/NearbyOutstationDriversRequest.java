package com.ridehail.driverservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NearbyOutstationDriversRequest {

    private String pickupDistrict;
    private String dropoffDistrict;
    private String vehicleTypeId;
    private String vehicleVariantId;
    private Double radiusMeters = 10000d;

    public String getPickupDistrict() {
        return pickupDistrict;
    }

    public void setPickupDistrict(String pickupDistrict) {
        this.pickupDistrict = pickupDistrict;
    }

    public String getDropoffDistrict() {
        return dropoffDistrict;
    }

    public void setDropoffDistrict(String dropoffDistrict) {
        this.dropoffDistrict = dropoffDistrict;
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
}
