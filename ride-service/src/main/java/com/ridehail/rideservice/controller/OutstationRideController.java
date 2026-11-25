package com.ridehail.rideservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ridehail.common.security.AuthContext;
import com.ridehail.rideservice.dto.AcceptOutstationRideRequest;
import com.ridehail.rideservice.dto.EndOutstationRideRequest;
import com.ridehail.rideservice.dto.GetOutstationRidesRequest;
import com.ridehail.rideservice.dto.NearbyDriversRequest;
import com.ridehail.rideservice.dto.OutstationFeedRequest;
import com.ridehail.rideservice.dto.OutstationRideRequest;
import com.ridehail.rideservice.dto.RentalSlotsRequest;
import com.ridehail.rideservice.service.OutstationRideService;

import jakarta.validation.Valid;

/**
 * Port of the outstation / pricing-lookup routes in routes/ride.routes.js (mounted at /ride-apis by
 * server.js line 89). Controllers hold no business logic - each delegates to
 * {@link OutstationRideService} and wraps the result in ApiResponseService.
 */
@RestController
@RequestMapping("/ride-apis")
public class OutstationRideController {

    private final OutstationRideService service;
    private final com.ridehail.common.response.ApiResponseService apiResponseService;

    public OutstationRideController(OutstationRideService service,
                                   com.ridehail.common.response.ApiResponseService apiResponseService) {
        this.service = service;
        this.apiResponseService = apiResponseService;
    }

    private static String authId() {
        AuthContext ctx = AuthContext.current();
        return ctx == null ? null : ctx.principalId();
    }

    @PostMapping("/create-outstation-ride")
    public ResponseEntity<?> createOutstationRide(@Valid @RequestBody OutstationRideRequest req) {
        return apiResponseService.ok("Outstation ride created", service.createOutstationRide(req, authId()));
    }

    @PostMapping("/accept-outstation-ride")
    public ResponseEntity<?> acceptOutstationRide(@Valid @RequestBody AcceptOutstationRideRequest req) {
        return apiResponseService.ok("Outstation ride accepted successfully",
                service.acceptOutstationRide(req, authId()));
    }

    @PostMapping("/update-outstation-ride")
    public ResponseEntity<?> updateOutstationRide(@Valid @RequestBody OutstationRideRequest req) {
        return apiResponseService.ok("Outstation ride updated", service.updateOutstationRide(req));
    }

    @PostMapping("/end-outstation-ride")
    public ResponseEntity<?> endOutstationRide(@Valid @RequestBody EndOutstationRideRequest req) {
        return apiResponseService.ok("Outstation ride completed", service.endOutstationRide(req));
    }

    @PostMapping("/nearby-drivers-available")
    public ResponseEntity<?> nearbyDriversAvailable(@Valid @RequestBody NearbyDriversRequest req) {
        return apiResponseService.ok("Nearby drivers with price", service.nearbyDriversAvailable(req));
    }

    @PostMapping("/outstation-rides")
    public ResponseEntity<?> getOutstationRides(@Valid @RequestBody GetOutstationRidesRequest req) {
        return apiResponseService.ok("Outstation rides fetched successfully", service.getOutstationRides(req));
    }

    @PostMapping("/outstation-ride-list")
    public ResponseEntity<?> getOutstationRideList(@RequestBody GetOutstationRidesRequest req) {
        return apiResponseService.ok("Outstation rides fetched successfully",
                service.getOutstationRideList(req));
    }

    @PostMapping("/outstation-feeds")
    public ResponseEntity<?> outstationFeeds(@Valid @RequestBody OutstationFeedRequest req) {
        return apiResponseService.ok("Outstation feed fetched successfully", service.outstationFeeds(req));
    }

    @PostMapping("/rental-slots")
    public ResponseEntity<?> getRentalSlots(@Valid @RequestBody RentalSlotsRequest req) {
        return apiResponseService.ok("Rental slots fetched successfully", service.getRentalSlots(req));
    }

    @GetMapping("/ride-payment-modes/{rideId}")
    public ResponseEntity<?> getRidePaymentModes(@PathVariable String rideId) {
        return apiResponseService.ok("Ride payment modes fetched successfully",
                service.getRidePaymentModes(rideId));
    }

    @GetMapping("/price-details/{id}")
    public ResponseEntity<?> getPriceDetailsById(@PathVariable String id) {
        return apiResponseService.ok("User ride payment details fetched", service.getPriceDetailsById(id));
    }
}
