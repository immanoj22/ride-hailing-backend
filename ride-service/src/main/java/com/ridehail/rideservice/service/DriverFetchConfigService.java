package com.ridehail.rideservice.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.rideservice.dto.UpdateDriverFetchConfigRequest;
import com.ridehail.rideservice.entity.DriverFetchConfig;
import com.ridehail.rideservice.repository.DriverFetchConfigRepository;

/** Ports ride.controller.js: get_driver_fetch_config, update_driver_fetch_config. */
@Service
@Transactional(readOnly = true)
public class DriverFetchConfigService {

    private final DriverFetchConfigRepository repository;

    public DriverFetchConfigService(DriverFetchConfigRepository repository) {
        this.repository = repository;
    }

    public Map<String, Object> get() {
        DriverFetchConfig config = repository.findFirstByOrderByCreatedAtDesc()
                .orElseThrow(() -> new ApiException(StatusCode.NOT_FOUND, "No driver fetch config found"));
        return toMap(config);
    }

    /** Returns [dataMap, created?] - controller uses created flag only for the response message. */
    @Transactional
    public UpdateResult update(UpdateDriverFetchConfigRequest body) {
        Optional<DriverFetchConfig> existing = repository.findFirstByOrderByCreatedAtDesc();

        if (existing.isEmpty()) {
            DriverFetchConfig config = DriverFetchConfig.builder()
                    .withinKm(body.getWithinKm() != null ? body.getWithinKm() : BigDecimal.ZERO)
                    .driverCount(body.getDriverCount() != null ? body.getDriverCount() : 0)
                    .requestPerDriver(body.getRequestPerDriver() != null ? body.getRequestPerDriver() : 0)
                    .kmRestriction(body.getKmRestriction() != null ? body.getKmRestriction() : BigDecimal.ZERO)
                    .build();
            config = repository.save(config);
            return new UpdateResult(toMap(config), true);
        }

        DriverFetchConfig config = existing.get();
        if (body.getWithinKm() != null) {
            config.setWithinKm(body.getWithinKm());
        }
        if (body.getDriverCount() != null) {
            config.setDriverCount(body.getDriverCount());
        }
        if (body.getRequestPerDriver() != null) {
            config.setRequestPerDriver(body.getRequestPerDriver());
        }
        if (body.getKmRestriction() != null) {
            config.setKmRestriction(body.getKmRestriction());
        }
        config = repository.save(config);
        return new UpdateResult(toMap(config), false);
    }

    public record UpdateResult(Map<String, Object> data, boolean created) {
    }

    private Map<String, Object> toMap(DriverFetchConfig c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", c.getId());
        m.put("withinKm", c.getWithinKm());
        m.put("driverCount", c.getDriverCount());
        m.put("requestPerDriver", c.getRequestPerDriver());
        m.put("kmRestriction", c.getKmRestriction());
        m.put("createdAt", c.getCreatedAt());
        m.put("updatedAt", c.getUpdatedAt());
        return m;
    }
}
