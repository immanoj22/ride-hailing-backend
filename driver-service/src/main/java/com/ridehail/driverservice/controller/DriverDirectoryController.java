package com.ridehail.driverservice.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ridehail.common.response.ApiResponseService;
import com.ridehail.driverservice.dto.DriversKycListRequest;
import com.ridehail.driverservice.dto.GetMultipleDriversByIdRequest;
import com.ridehail.driverservice.dto.KycVerifyRequest;
import com.ridehail.driverservice.dto.UpdateDriverTripRequest;
import com.ridehail.driverservice.dto.UpdateRideHailDriverRequest;
import com.ridehail.driverservice.service.DriverDirectoryService;

import jakarta.validation.Valid;

/**
 * Direct port of driver.controller.js's getDriverById / getDriverDetails / getDriversStatus /
 * getMultipleDriversById / searchDrivers / getUnverifiedDrivers / kyc_verify / drivers_kyc_list /
 * getDrivers / update_ridehailDrivers / get_profiles / update_driver_trip
 * (routes/driver.routes.js, prefix /driver-apis).
 */
@RestController
@RequestMapping("/driver-apis")
public class DriverDirectoryController {

    private final DriverDirectoryService driverDirectoryService;
    private final ApiResponseService apiResponseService;

    public DriverDirectoryController(DriverDirectoryService driverDirectoryService,
            ApiResponseService apiResponseService) {
        this.driverDirectoryService = driverDirectoryService;
        this.apiResponseService = apiResponseService;
    }

    @GetMapping("/driverid/{id}")
    public ResponseEntity<?> getDriverById(@PathVariable String id) {
        return apiResponseService.ok("Driver Data Fetched", driverDirectoryService.getDriverById(id));
    }

    @GetMapping("/driver-details/{driverId}")
    public ResponseEntity<?> getDriverDetails(@PathVariable String driverId) {
        return apiResponseService.ok("Driver details fetched successfully",
                driverDirectoryService.getDriverDetails(driverId));
    }

    @GetMapping("/status")
    public ResponseEntity<?> getDriversStatus(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "driverStatus", required = false) String driverStatus) {
        return apiResponseService.ok("Driver List",
                driverDirectoryService.getDriversStatus(page, limit, status, driverStatus));
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchDrivers(@RequestParam(value = "name", required = false) String name) {
        return apiResponseService.ok("Search Result", driverDirectoryService.searchDrivers(name));
    }

    @GetMapping("/unverified")
    public ResponseEntity<?> getUnverifiedDrivers(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return apiResponseService.ok("Unverified Drivers", driverDirectoryService.getUnverifiedDrivers(page, limit));
    }

    @PostMapping("/getMultipleDriversById")
    public ResponseEntity<?> getMultipleDriversById(@RequestBody(required = false) GetMultipleDriversByIdRequest request) {
        return apiResponseService.ok("Driver datas fetched", driverDirectoryService.getMultipleDriversById(request));
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfiles(@RequestParam(value = "driverIds", required = false) Object driverIds) {
        return apiResponseService.ok("Driver profiles fetched successfully",
                driverDirectoryService.getProfiles(driverIds));
    }

    @GetMapping("/getDrivers")
    public ResponseEntity<?> getDrivers(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return apiResponseService.ok("Drivers profile fetched successfully", driverDirectoryService.getDrivers(page, limit));
    }

    @PostMapping("/update-ridehaildrivers")
    public ResponseEntity<?> updateRideHailDrivers(@Valid @RequestBody UpdateRideHailDriverRequest request) {
        return apiResponseService.ok(updateRideHailDriversMessage(request), driverDirectoryService.updateRideHailDrivers(request));
    }

    private String updateRideHailDriversMessage(UpdateRideHailDriverRequest request) {
        if ("updatestatus".equals(request.getType())) {
            return "Updated Successfully";
        }
        if ("delete".equals(request.getType())) {
            return "Deleted Successfully";
        }
        if ("vehiclenumber".equals(request.getAction())) {
            return "VehicleNumber Updated Successfully";
        }
        if ("vehicletype".equals(request.getAction())) {
            return "vehicleType Updated Successfully";
        }
        return "Updated Successfully";
    }

    @PostMapping("/kycverify")
    public ResponseEntity<?> kycVerify(@Valid @RequestBody KycVerifyRequest request) {
        driverDirectoryService.kycVerify(request);
        return apiResponseService.ok("Status Updated Successfully", null);
    }

    @PostMapping("/drivers-kyc-list")
    public ResponseEntity<?> driversKycList(@RequestBody(required = false) DriversKycListRequest request) {
        DriversKycListRequest effectiveRequest = request == null ? new DriversKycListRequest() : request;
        Map<String, Object> result = driverDirectoryService.driversKycList(effectiveRequest);
        boolean empty = ((java.util.List<?>) result.get("drivers")).isEmpty();
        return apiResponseService.ok(empty ? "No drivers found" : "Drivers fetched successfully", result);
    }

    @PostMapping("/update-driver-trip")
    public ResponseEntity<?> updateDriverTrip(@RequestBody(required = false) UpdateDriverTripRequest request) {
        boolean updated = request != null && driverDirectoryService.updateDriverTrip(
                request.getDriverId(), request.getTripId(), request.getStatus());
        if (!updated) {
            return apiResponseService.error(400, "Driver trip not updated");
        }
        return apiResponseService.ok("Driver trip updated successfully", null);
    }
}
