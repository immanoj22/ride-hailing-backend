package com.ridehail.rideservice.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ridehail.common.response.ApiResponseService;
import com.ridehail.common.security.AuthContext;
import com.ridehail.rideservice.dto.AddGpsPointRequest;
import com.ridehail.rideservice.dto.CancelRideByUserRequest;
import com.ridehail.rideservice.dto.CreateRideRequest;
import com.ridehail.rideservice.dto.UpdateRideRequest;
import com.ridehail.rideservice.service.RideLifecycleService;

import jakarta.validation.Valid;

/**
 * Ride lifecycle REST endpoints (routes/ride.routes.js, mounted at /ride-apis - server.js line 89).
 * Endpoints preserved verbatim:
 *   POST   /ride-apis/rides                 -> create_ride
 *   GET    /ride-apis/get-all-rides         -> get_all_ride
 *   GET    /ride-apis/rides/{rideId}        -> get_ride
 *   POST   /ride-apis/accept-ride           -> acceptRide
 *   POST   /ride-apis/end-ride              -> dropoffRide
 *   POST   /ride-apis/addGPSPoint           -> addGPSPoint
 *   GET    /ride-apis/getGPSPoint/{rideId}  -> getGPSPoint
 *   PATCH  /ride-apis/update-ride           -> update_ride
 *   PATCH  /ride-apis/cancel-ride-by-user   -> cancelRideByUser
 * All other lifecycle functions (pickupRide/startRide/ride_completePayment/endRide/updateVehicleType/
 * fetchRide/updateIgnoredDriver/rideCancelByDriver/suddenendByUser) have no route - they run only
 * off Redis pub/sub (helpers/ride.subscriber.js), wired in RideLifecyclePubSubConfig.
 */
@RestController
public class RideLifecycleController {

    private final RideLifecycleService service;
    private final ApiResponseService apiResponseService;
    private final ObjectMapper mapper = new ObjectMapper();

    public RideLifecycleController(RideLifecycleService service, ApiResponseService apiResponseService) {
        this.service = service;
        this.apiResponseService = apiResponseService;
    }

    @PostMapping("/ride-apis/rides")
    public ResponseEntity<?> createRide(@Valid @RequestBody CreateRideRequest req) {
        return apiResponseService.ok("Ride created", service.createRide(req));
    }

    @GetMapping("/ride-apis/get-all-rides")
    public ResponseEntity<?> getAllRides(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) String rideType,
            @RequestParam(required = false) String search) {
        return apiResponseService.ok("Rides fetched", service.getAllRide(page, limit, fromDate, toDate, rideType, search));
    }

    @GetMapping("/ride-apis/rides/{rideId}")
    public ResponseEntity<?> getRide(@PathVariable String rideId) {
        return apiResponseService.ok("Ride fetched", service.getRide(rideId));
    }

    @PostMapping("/ride-apis/accept-ride")
    public ResponseEntity<?> acceptRide(@RequestBody JsonNode body) {
        ObjectNode payload = body.deepCopy();
        AuthContext ctx = AuthContext.current();
        if (ctx != null && ctx.principal() != null) {
            payload.set("driver", ctx.principal());
        }
        service.acceptRide(payload);
        return apiResponseService.ok("trip accepted", Map.of());
    }

    @PostMapping("/ride-apis/end-ride")
    public ResponseEntity<?> dropoffRide(@RequestBody JsonNode body) {
        Map<String, Object> result = service.dropoffRide(body.path("_id").asText(null),
                body.path("driverId").asText(null));
        return apiResponseService.ok("User dropped off successfully", result);
    }

    @PostMapping("/ride-apis/addGPSPoint")
    public ResponseEntity<?> addGPSPoint(@Valid @RequestBody AddGpsPointRequest req) {
        return apiResponseService.ok("The GPS Point has been added", service.addGPSPoint(req));
    }

    @GetMapping("/ride-apis/getGPSPoint/{rideId}")
    public ResponseEntity<?> getGPSPoint(@PathVariable String rideId) {
        return apiResponseService.ok("GPS Details are available", service.getGPSPoint(rideId));
    }

    @PatchMapping("/ride-apis/update-ride")
    public ResponseEntity<?> updateRide(@Valid @RequestBody UpdateRideRequest req) {
        return apiResponseService.ok("Ride updaed", service.updateRide(req));
    }

    @PatchMapping("/ride-apis/cancel-ride-by-user")
    public ResponseEntity<?> cancelRideByUser(@Valid @RequestBody CancelRideByUserRequest req) {
        return apiResponseService.ok("Ride cancelled successfully", service.cancelRideByUser(req));
    }
}
