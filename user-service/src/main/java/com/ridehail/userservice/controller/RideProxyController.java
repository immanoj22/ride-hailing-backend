package com.ridehail.userservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.ridehail.common.response.ApiResponseService;
import com.ridehail.userservice.dto.OutstationRidesRequest;
import com.ridehail.userservice.dto.TriggerSosRequest;
import com.ridehail.userservice.service.RideProxyService;
import com.ridehail.userservice.service.RideProxyService.ProxyResult;

/**
 * "ride-service-proxy" bucket - direct port of the ride/coupon/SOS/support-chat pass-through
 * endpoints in controller/user.controller.js (mounted at "/user-apis" per server.js). No business
 * logic lives here; every path just calls RideProxyService and wraps the result via
 * ApiResponseService, exactly like the legacy sendResponse(...) calls.
 */
@RestController
@RequestMapping("/user-apis")
public class RideProxyController {

    private final RideProxyService rideProxyService;
    private final ApiResponseService apiResponseService;

    public RideProxyController(RideProxyService rideProxyService, ApiResponseService apiResponseService) {
        this.rideProxyService = rideProxyService;
        this.apiResponseService = apiResponseService;
    }

    /** GET /rides/:rideId */
    @GetMapping("/rides/{rideId}")
    public ResponseEntity<?> getRide(@PathVariable String rideId) {
        ProxyResult result = rideProxyService.getRide(rideId);
        return apiResponseService.ok(result.message(), result.data());
    }

    /** POST /outstation/rides */
    @PostMapping("/outstation/rides")
    public ResponseEntity<?> getOutstationRides(@RequestBody(required = false) OutstationRidesRequest request) {
        ProxyResult result = rideProxyService.getOutstationRides(request != null ? request : new OutstationRidesRequest());
        return apiResponseService.ok(result.message(), result.data());
    }

    /** POST /outstation/update */
    @PostMapping("/outstation/update")
    public ResponseEntity<?> updateOutstationRide(@RequestBody(required = false) JsonNode body) {
        ProxyResult result = rideProxyService.updateOutstationRide(body);
        return apiResponseService.ok(result.message(), result.data());
    }

    /**
     * GET rental-slots/:vehicleVariantId in the original (missing leading slash - see the Javadoc on
     * RideProxyService#getRentalSlots for why that made the route unreachable); mapped here at the
     * clearly-intended "/rental-slots/{vehicleVariantId}".
     */
    @GetMapping("/rental-slots/{vehicleVariantId}")
    public ResponseEntity<?> getRentalSlots(@PathVariable String vehicleVariantId) {
        ProxyResult result = rideProxyService.getRentalSlots(vehicleVariantId);
        return apiResponseService.ok(result.message(), result.data());
    }

    /** GET /get-coupons/:tripType/:couponId */
    @GetMapping("/get-coupons/{tripType}/{couponId}")
    public ResponseEntity<?> getCoupons(@PathVariable String tripType, @PathVariable String couponId) {
        ProxyResult result = rideProxyService.getCoupons(tripType, couponId);
        return apiResponseService.ok(result.message(), result.data());
    }

    /** POST /trigger-sos */
    @PostMapping("/trigger-sos")
    public ResponseEntity<?> triggerSos(@RequestBody(required = false) TriggerSosRequest request) {
        ProxyResult result = rideProxyService.triggerSos(request);
        return apiResponseService.ok(result.message(), result.data());
    }

    /** GET /support-ticket-chats?ticketId=... */
    @GetMapping("/support-ticket-chats")
    public ResponseEntity<?> getSupportTicketChats(@RequestParam String ticketId) {
        ProxyResult result = rideProxyService.getSupportTicketChats(ticketId);
        return apiResponseService.ok(result.message(), result.data());
    }
}
