package com.ridehail.driverservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotBlank;

/** Equivalent of validations/driver.validation.js's updateRideHailDriverSchema. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateRideHailDriverRequest {

    @NotBlank(message = "Driver ID is required")
    private String _id;

    /** "updatestatus" | "delete" | null. */
    private String type;

    @NotBlank(message = "Status value is required in action")
    private String action;

    private String vehicleNumber;
    private String vehicleType;

    public String get_id() {
        return _id;
    }

    public void set_id(String _id) {
        this._id = _id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public void setVehicleNumber(String vehicleNumber) {
        this.vehicleNumber = vehicleNumber;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }
}
