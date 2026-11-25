package com.ridehail.driverservice.service;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ridehail.driverservice.entity.Driver;
import com.ridehail.driverservice.entity.DriverActivity;
import com.ridehail.driverservice.repository.DriverActivityRepository;
import com.ridehail.driverservice.repository.DriverRepository;
import com.ridehail.driverservice.util.RequestMetaUtil;

import jakarta.servlet.http.HttpServletRequest;

/** Equivalent of helpers/driver.commonFunction.js's logActivity({req, driverId, action, meta}). */
@Component
public class DriverActivityLogger {

    private final DriverActivityRepository driverActivityRepository;
    private final DriverRepository driverRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DriverActivityLogger(DriverActivityRepository driverActivityRepository, DriverRepository driverRepository) {
        this.driverActivityRepository = driverActivityRepository;
        this.driverRepository = driverRepository;
    }

    public void log(HttpServletRequest request, UUID driverId, String action) {
        log(request, driverId, action, objectMapper.createObjectNode());
    }

    public void log(HttpServletRequest request, UUID driverId, String action, JsonNode meta) {
        if (driverId == null) {
            return;
        }
        Driver driverRef = driverRepository.getReferenceById(driverId);
        DriverActivity activity = DriverActivity.builder()
                .driver(driverRef)
                .action(action)
                .ip(RequestMetaUtil.ip(request))
                .device(RequestMetaUtil.device(request))
                .meta(meta == null ? objectMapper.createObjectNode() : meta)
                .build();
        try {
            driverActivityRepository.save(activity);
        } catch (Exception e) {
            // Mirrors the original's fire-and-forget, error-swallowing logActivity call - an
            // activity-log failure must never fail the enclosing request.
        }
    }

    public ObjectNode meta() {
        return objectMapper.createObjectNode();
    }

    public ObjectMapper mapper() {
        return objectMapper;
    }
}
