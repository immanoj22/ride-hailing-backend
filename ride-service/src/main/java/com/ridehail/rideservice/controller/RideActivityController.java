package com.ridehail.rideservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ridehail.common.response.ApiResponseService;
import com.ridehail.rideservice.service.RideActivityChartService;

/** Ports ride.controller.js: get_ride_activity_chart (GET /ride-apis/ride-activity). */
@RestController
@RequestMapping("/ride-apis")
public class RideActivityController {

    private final RideActivityChartService service;
    private final ApiResponseService apiResponseService;

    public RideActivityController(RideActivityChartService service, ApiResponseService apiResponseService) {
        this.service = service;
        this.apiResponseService = apiResponseService;
    }

    @GetMapping("/ride-activity")
    public ResponseEntity<?> getRideActivityChart(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return apiResponseService.ok("Ride activity fetched", service.getRideActivityChart(from, to));
    }
}
