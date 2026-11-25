package com.ridehail.commonservice.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ridehail.common.response.ApiResponseService;
import com.ridehail.commonservice.dto.GetEstimatedTimeRequest;
import com.ridehail.commonservice.dto.GetRouteDetailsRequest;
import com.ridehail.commonservice.service.GeoLocationService;

import jakarta.validation.Valid;

/** Direct port of controllers/geoLocation.controller.js. */
@RestController
@RequestMapping("/common-apis/geolocation")
public class GeoLocationController {

    private final GeoLocationService geoLocationService;
    private final ApiResponseService apiResponseService;

    public GeoLocationController(GeoLocationService geoLocationService, ApiResponseService apiResponseService) {
        this.geoLocationService = geoLocationService;
        this.apiResponseService = apiResponseService;
    }

    @GetMapping("/get-address-by-lat-lng")
    public ResponseEntity<?> getAddressByLatLng(
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(required = false) String from) {
        Map<String, Object> data = geoLocationService.getAddressByLatLng(lat, lng, from);
        return apiResponseService.ok("Address fetched successfully.", data);
    }

    @PostMapping("/get-estimate-time-by-location")
    public ResponseEntity<?> getEstimatedTime(@Valid @RequestBody GetEstimatedTimeRequest request) {
        Map<String, Object> data = geoLocationService.getEstimatedTime(request);
        return apiResponseService.ok("Address fetched successfully.", data);
    }

    @PostMapping("/get-distance-and-duration")
    public ResponseEntity<?> getRouteDetails(@Valid @RequestBody GetRouteDetailsRequest request) {
        Map<String, Object> data = geoLocationService.getRouteDetails(request);
        return apiResponseService.ok("Address fetched successfully.", data);
    }
}
