package com.ridehail.rideservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ridehail.common.response.ApiResponseService;
import com.ridehail.rideservice.service.AdminOngoingRideService;

/** Ports ride.controller.js: get_ongoing_rides_admin, get_ongoing_ride_gps (prefix /ride-apis). */
@RestController
@RequestMapping("/ride-apis")
public class AdminOngoingRideController {

    private final AdminOngoingRideService service;
    private final ApiResponseService apiResponseService;

    public AdminOngoingRideController(AdminOngoingRideService service, ApiResponseService apiResponseService) {
        this.service = service;
        this.apiResponseService = apiResponseService;
    }

    @GetMapping("/ongoing-rides")
    public ResponseEntity<?> getOngoingRidesAdmin(
            @RequestParam(defaultValue = "all") String rideType,
            @RequestParam(required = false) String rideId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "true") boolean includeGPS,
            @RequestParam(defaultValue = "true") boolean includeProfiles,
            @RequestParam(defaultValue = "newest") String sort) {
        return apiResponseService.ok("Ongoing rides fetched",
                service.getOngoingRidesAdmin(rideType, rideId, page, limit, includeGPS, includeProfiles, sort));
    }

    @GetMapping("/ongoing-rides/{rideId}/gps")
    public ResponseEntity<?> getOngoingRideGps(
            @PathVariable String rideId,
            @RequestParam(defaultValue = "1") int limit) {
        return apiResponseService.ok("GPS details fetched", service.getOngoingRideGps(rideId, limit));
    }
}
