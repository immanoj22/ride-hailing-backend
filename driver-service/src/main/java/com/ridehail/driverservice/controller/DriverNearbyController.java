package com.ridehail.driverservice.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.common.response.ApiResponseService;
import com.ridehail.common.security.AuthContext;
import com.ridehail.driverservice.dto.AcceptOutstationRideRequest;
import com.ridehail.driverservice.dto.GetOutstationRidesRequest;
import com.ridehail.driverservice.dto.NearbyDriversRequest;
import com.ridehail.driverservice.dto.NearbyOutstationDriversRequest;
import com.ridehail.driverservice.dto.OutstationFeedsRequest;
import com.ridehail.driverservice.dto.TriggerSosRequest;
import com.ridehail.driverservice.service.DriverNearbyService;

import jakarta.validation.Valid;

/**
 * Direct port of driver.controller.js's nearby_drivers, nearby_outstation_drivers,
 * acceptOutstationRide, get_outstation_rides, outstation_feeds, get_ride, trigger_sos and
 * get_support_ticket_chats (routes/driver.routes.js, mounted at "/driver-apis").
 *
 * <p>No business logic here - everything is delegated to {@link DriverNearbyService}.
 */
@RestController
@RequestMapping("/driver-apis")
public class DriverNearbyController {

    private final DriverNearbyService driverNearbyService;
    private final ApiResponseService apiResponseService;

    public DriverNearbyController(DriverNearbyService driverNearbyService, ApiResponseService apiResponseService) {
        this.driverNearbyService = driverNearbyService;
        this.apiResponseService = apiResponseService;
    }

    @PostMapping("/nearby-drivers")
    public ResponseEntity<?> nearbyDrivers(@RequestBody(required = false) NearbyDriversRequest request) {
        List<Map<String, Object>> data = driverNearbyService.nearbyDrivers(
                request != null ? request : new NearbyDriversRequest());
        return apiResponseService.ok("Nearby drivers fetched", data);
    }

    @PostMapping("/nearby-outstation-drivers")
    public ResponseEntity<?> nearbyOutstationDrivers(@RequestBody(required = false) NearbyOutstationDriversRequest request) {
        List<Map<String, Object>> data = driverNearbyService.nearbyOutstationDrivers(
                request != null ? request : new NearbyOutstationDriversRequest());
        return apiResponseService.ok("Outstation drivers fetched", data);
    }

    @PostMapping("/outstation/accept")
    public ResponseEntity<?> acceptOutstationRide(@Valid @RequestBody AcceptOutstationRideRequest request) {
        String driverId = requireAuthDriverId();
        Map<String, Object> result = driverNearbyService.acceptOutstationRide(driverId, request);
        return apiResponseService.ok((String) result.get("message"), result.get("data"));
    }

    @PostMapping("/outstation/rides")
    public ResponseEntity<?> getOutstationRides(@RequestBody(required = false) GetOutstationRidesRequest request) {
        String driverId = requireAuthDriverId();
        Map<String, Object> result = driverNearbyService.getOutstationRides(
                driverId, request != null ? request : new GetOutstationRidesRequest());
        return apiResponseService.ok((String) result.get("message"), result.get("data"));
    }

    @PostMapping("/outstation/feeds")
    public ResponseEntity<?> outstationFeeds(@RequestBody(required = false) OutstationFeedsRequest request) {
        String driverId = requireAuthDriverId();
        Map<String, Object> result = driverNearbyService.outstationFeeds(
                driverId, request != null ? request : new OutstationFeedsRequest());
        return apiResponseService.ok((String) result.get("message"), result.get("data"));
    }

    @GetMapping("/rides/{rideId}")
    public ResponseEntity<?> getRide(@PathVariable String rideId) {
        String driverId = requireAuthDriverId();
        Map<String, Object> result = driverNearbyService.getRide(driverId, rideId);
        return apiResponseService.ok((String) result.get("message"), result.get("data"));
    }

    @PostMapping("/trigger-sos")
    public ResponseEntity<?> triggerSos(@RequestBody(required = false) TriggerSosRequest request) {
        String driverId = requireAuthDriverId();
        Map<String, Object> result = driverNearbyService.triggerSos(
                driverId, request != null ? request : new TriggerSosRequest());
        return apiResponseService.ok("SOS triggered successfully", result.get("data"));
    }

    @GetMapping("/support-ticket-chats")
    public ResponseEntity<?> getSupportTicketChats(@RequestParam(value = "ticketId", required = false) String ticketId) {
        String driverId = requireAuthDriverId();
        Map<String, Object> result = driverNearbyService.getSupportTicketChats(driverId, ticketId);
        return apiResponseService.ok((String) result.get("message"), result.get("data"));
    }

    private String requireAuthDriverId() {
        AuthContext ctx = AuthContext.current();
        if (ctx == null || ctx.principalId() == null) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Not authorized");
        }
        return ctx.principalId();
    }
}
