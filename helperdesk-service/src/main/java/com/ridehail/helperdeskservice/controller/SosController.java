package com.ridehail.helperdeskservice.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ridehail.common.response.ApiResponseService;
import com.ridehail.helperdeskservice.dto.ListSosRequest;
import com.ridehail.helperdeskservice.dto.SosResponse;
import com.ridehail.helperdeskservice.dto.TriggerSosRequest;
import com.ridehail.helperdeskservice.service.SosService;

import jakarta.validation.Valid;

/** Port of routes/helperdesk.routes.js's /sos/* routes (mounted under "/helperdesk-apis"). */
@RestController
@RequestMapping("/helperdesk-apis")
public class SosController {

    private final SosService sosService;
    private final ApiResponseService apiResponseService;

    public SosController(SosService sosService, ApiResponseService apiResponseService) {
        this.sosService = sosService;
        this.apiResponseService = apiResponseService;
    }

    @PostMapping("/sos/trigger")
    public ResponseEntity<?> triggerSos(@Valid @RequestBody TriggerSosRequest request) {
        SosResponse data = sosService.triggerSos(request);
        return apiResponseService.ok("SOS triggered successfully", data);
    }

    @PostMapping("/sos/list")
    public ResponseEntity<?> listSos(@RequestBody(required = false) ListSosRequest request) {
        Map<String, Object> data = sosService.listSos(request == null ? new ListSosRequest() : request);
        return apiResponseService.ok("SOS list fetched successfully", data);
    }
}
