package com.ridehail.rideservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ridehail.common.response.ApiResponseService;
import com.ridehail.rideservice.dto.ClaimDriverRidePaymentsRequest;
import com.ridehail.rideservice.dto.DriverIdRequest;
import com.ridehail.rideservice.dto.DriverRidePaymentsRequest;
import com.ridehail.rideservice.dto.ReferralDriverRidePaymentsRequest;
import com.ridehail.rideservice.service.RidePaymentsService;

import jakarta.validation.Valid;

/**
 * Ports ride.controller.js: getDriverRidePayments, claimDriverRidePayments,
 * getDriverReferralEarnings, getReferralDriverRidePayments (routes/ride.routes.js, prefix /ride-apis).
 */
@RestController
@RequestMapping("/ride-apis")
public class RidePaymentsController {

    private final RidePaymentsService service;
    private final ApiResponseService apiResponseService;

    public RidePaymentsController(RidePaymentsService service, ApiResponseService apiResponseService) {
        this.service = service;
        this.apiResponseService = apiResponseService;
    }

    @PostMapping("/driver-ride-payments")
    public ResponseEntity<?> getDriverRidePayments(@RequestBody DriverRidePaymentsRequest body) {
        return apiResponseService.ok("Ride payment details fetched successfully",
                service.getDriverRidePayments(body.getDriverId(), body.getIsClaimed(), body.getPage(), body.getLimit()));
    }

    @PostMapping("/driver-ride-claim")
    public ResponseEntity<?> claimDriverRidePayments(@Valid @RequestBody ClaimDriverRidePaymentsRequest body) {
        return apiResponseService.ok("Ride payments marked as claimed",
                service.claimDriverRidePayments(body.getDriverId()));
    }

    @PostMapping("/driver-referral-earnings")
    public ResponseEntity<?> getDriverReferralEarnings(@RequestBody DriverIdRequest body) {
        return apiResponseService.ok("Driver referral earnings fetched successfully",
                service.getDriverReferralEarnings(body.getDriverId()));
    }

    @PostMapping("/ref-driver-ride-payments")
    public ResponseEntity<?> getReferralDriverRidePayments(@RequestBody ReferralDriverRidePaymentsRequest body) {
        return apiResponseService.ok("Referral driver ride payment details fetched successfully",
                service.getReferralDriverRidePayments(body.getRefDriverId(), body.getOwnerDriverId()));
    }
}
