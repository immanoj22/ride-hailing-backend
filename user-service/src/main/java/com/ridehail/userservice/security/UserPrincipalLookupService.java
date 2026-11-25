package com.ridehail.userservice.security;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehail.common.constants.AuthUserType;
import com.ridehail.common.security.InternalServiceClient;
import com.ridehail.common.security.PrincipalLookupService;
import com.ridehail.userservice.repository.UserRepository;

/**
 * user-service OWNS the USER type: middleware/user.auth.middleware.js resolves "user" tokens via
 * its own service/user.services.js#getUserById (a local DB lookup, with no additional
 * enabled/soft-delete check - the legacy authMiddleware only checks "does this id exist"), so we
 * reproduce that exactly against our own UserRepository. DRIVER and ADMIN are remote lookups to
 * the owning service, matching utills/user.axios.js#getDriverById ("/profile/{id}" on
 * DRIVER_SERVICE_URL) and #getAdminById ("/get-admin/{id}" on ADMIN_SERVICE_URL).
 */
@Component
public class UserPrincipalLookupService implements PrincipalLookupService {

    private final UserRepository userRepository;
    private final InternalServiceClient internalServiceClient;
    private final ObjectMapper objectMapper;
    private final String driverServiceUrl;
    private final String adminServiceUrl;

    public UserPrincipalLookupService(
            UserRepository userRepository,
            InternalServiceClient internalServiceClient,
            ObjectMapper objectMapper,
            @Value("${ridehail.services.driver-url}") String driverServiceUrl,
            @Value("${ridehail.services.admin-url}") String adminServiceUrl) {
        this.userRepository = userRepository;
        this.internalServiceClient = internalServiceClient;
        this.objectMapper = objectMapper;
        this.driverServiceUrl = driverServiceUrl;
        this.adminServiceUrl = adminServiceUrl;
    }

    @Override
    public JsonNode lookup(AuthUserType type, String id) {
        if (id == null) {
            return null;
        }
        return switch (type) {
            case USER -> lookupLocalUser(id);
            case DRIVER -> lookupRemote(driverServiceUrl + "/profile/" + id);
            case ADMIN -> lookupRemote(adminServiceUrl + "/get-admin/" + id);
            default -> null;
        };
    }

    private JsonNode lookupLocalUser(String id) {
        try {
            UUID userId = UUID.fromString(id);
            return userRepository.findById(userId)
                    .<JsonNode>map(objectMapper::valueToTree)
                    .orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private JsonNode lookupRemote(String url) {
        JsonNode response = internalServiceClient.get(url);
        if (response == null || !response.path("status").asBoolean(false)) {
            return null;
        }
        return response.path("data");
    }
}
