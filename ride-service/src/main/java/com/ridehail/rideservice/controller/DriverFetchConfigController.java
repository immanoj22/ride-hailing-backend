package com.ridehail.rideservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ridehail.common.response.ApiResponseService;
import com.ridehail.rideservice.dto.UpdateDriverFetchConfigRequest;
import com.ridehail.rideservice.service.DriverFetchConfigService;

/** Ports ride.controller.js: get_driver_fetch_config, update_driver_fetch_config (prefix /ride-apis). */
@RestController
@RequestMapping("/ride-apis")
public class DriverFetchConfigController {

    private final DriverFetchConfigService service;
    private final ApiResponseService apiResponseService;

    public DriverFetchConfigController(DriverFetchConfigService service, ApiResponseService apiResponseService) {
        this.service = service;
        this.apiResponseService = apiResponseService;
    }

    @GetMapping("/get-driver-fetch-config")
    public ResponseEntity<?> get() {
        return apiResponseService.ok("Driver fetch config fetched successfully", service.get());
    }

    @PostMapping("/update-driver-fetch-config")
    public ResponseEntity<?> update(@RequestBody UpdateDriverFetchConfigRequest body) {
        DriverFetchConfigService.UpdateResult result = service.update(body);
        String message = result.created()
                ? "Driver fetch config created successfully" : "Driver fetch config updated successfully";
        return apiResponseService.ok(message, result.data());
    }
}
