package com.ridehail.socketservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.ridehail.common.constants.AuthUserType;
import com.ridehail.common.security.InternalServiceClient;
import com.ridehail.common.security.PrincipalLookupService;

/**
 * socket-service owns no user/driver/admin data itself - middleware/socket.auth.middleware.js's
 * authMiddleware (and socketAuthMiddleware, for the Socket.IO handshake) resolves every one of
 * USER/DRIVER/ADMIN via utills/socket.axios.js's getUserById/getDriverById/getAdminById, all of
 * which are plain axios calls to the owning service. Mirrors
 * common-service's CommonPrincipalLookupService exactly.
 */
@Component
public class SocketPrincipalLookupService implements PrincipalLookupService {

    private final InternalServiceClient internalServiceClient;
    private final String userServiceUrl;
    private final String driverServiceUrl;
    private final String adminServiceUrl;

    public SocketPrincipalLookupService(
            InternalServiceClient internalServiceClient,
            @Value("${ridehail.services.user-url}") String userServiceUrl,
            @Value("${ridehail.services.driver-url}") String driverServiceUrl,
            @Value("${ridehail.services.admin-url}") String adminServiceUrl) {
        this.internalServiceClient = internalServiceClient;
        this.userServiceUrl = userServiceUrl;
        this.driverServiceUrl = driverServiceUrl;
        this.adminServiceUrl = adminServiceUrl;
    }

    @Override
    public JsonNode lookup(AuthUserType type, String id) {
        if (id == null) {
            return null;
        }
        JsonNode response = switch (type) {
            case USER -> internalServiceClient.get(userServiceUrl + "/profile/" + id);
            case DRIVER -> internalServiceClient.get(driverServiceUrl + "/profile/" + id);
            case ADMIN -> internalServiceClient.get(adminServiceUrl + "/get-admin/" + id);
            default -> null;
        };
        if (response == null || !response.path("status").asBoolean(false)) {
            return null;
        }
        return response.path("data");
    }
}
