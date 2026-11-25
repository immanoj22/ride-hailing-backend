package com.ridehail.driverservice.security;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehail.common.constants.AuthUserType;
import com.ridehail.common.security.InternalServiceClient;
import com.ridehail.common.security.PrincipalLookupService;
import com.ridehail.driverservice.entity.Driver;
import com.ridehail.driverservice.repository.DriverRepository;

/**
 * driver-service owns the DRIVER type - looked up locally against its own JPA repository, exactly
 * like the original driver.auth.middleware.js's {@code auth_user === "driver"} branch (which calls
 * the in-process {@code getDriverById} service function, no axios hop). USER and ADMIN are looked
 * up remotely via {@link InternalServiceClient}, mirroring that same middleware's
 * {@code getUserById}/{@code getAdminById} axios calls (utills/driver.axios.js).
 */
@Component
public class DriverPrincipalLookupService implements PrincipalLookupService {

    private final DriverRepository driverRepository;
    private final InternalServiceClient internalServiceClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String userServiceUrl;
    private final String adminServiceUrl;

    public DriverPrincipalLookupService(
            DriverRepository driverRepository,
            InternalServiceClient internalServiceClient,
            @Value("${ridehail.services.user-url}") String userServiceUrl,
            @Value("${ridehail.services.admin-url}") String adminServiceUrl) {
        this.driverRepository = driverRepository;
        this.internalServiceClient = internalServiceClient;
        this.userServiceUrl = userServiceUrl;
        this.adminServiceUrl = adminServiceUrl;
    }

    @Override
    public JsonNode lookup(AuthUserType type, String id) {
        if (id == null) {
            return null;
        }
        return switch (type) {
            case DRIVER -> lookupLocalDriver(id);
            case USER -> lookupRemote(userServiceUrl + "/profile/" + id);
            case ADMIN -> lookupRemote(adminServiceUrl + "/get-admin/" + id);
            default -> null;
        };
    }

    private JsonNode lookupLocalDriver(String id) {
        UUID driverId;
        try {
            driverId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return driverRepository.findById(driverId)
                .filter(driver -> !driver.isDeleted())
                .map(this::toJson)
                .orElse(null);
    }

    private JsonNode toJson(Driver driver) {
        return objectMapper.valueToTree(driver);
    }

    private JsonNode lookupRemote(String url) {
        JsonNode response = internalServiceClient.get(url);
        if (response == null || !response.path("status").asBoolean(false)) {
            return null;
        }
        return response.path("data");
    }
}
