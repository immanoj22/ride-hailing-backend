package com.ridehail.adminservice.security;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ridehail.adminservice.entity.Admin;
import com.ridehail.adminservice.repository.AdminRepository;
import com.ridehail.common.constants.AuthUserType;
import com.ridehail.common.security.InternalServiceClient;
import com.ridehail.common.security.PrincipalLookupService;

/**
 * Direct port of Admin-service/middleware/admin.auth.middleware.js's authMiddleware:
 * admin-service OWNS the "admin" auth type and resolves it against its own database
 * (the original called {@code getAdmin({_id: new ObjectId(verify.id)})} against the local
 * "admin" Mongoose model); "user" and "driver" are resolved remotely exactly like the original
 * {@code getUserById}/{@code getDriverById} axios helpers (utills/admin.axios.js):
 *   - user:   GET {USER_SERVICE_URL}/profile?userId={id}   -> principal is response.data.user
 *   - driver: GET {DRIVER_SERVICE_URL}/driver-details/{id} -> principal is response.data
 */
@Component
public class AdminPrincipalLookupService implements PrincipalLookupService {

    private final AdminRepository adminRepository;
    private final InternalServiceClient internalServiceClient;
    private final String userServiceUrl;
    private final String driverServiceUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminPrincipalLookupService(
            AdminRepository adminRepository,
            InternalServiceClient internalServiceClient,
            @Value("${ridehail.services.user-url}") String userServiceUrl,
            @Value("${ridehail.services.driver-url}") String driverServiceUrl) {
        this.adminRepository = adminRepository;
        this.internalServiceClient = internalServiceClient;
        this.userServiceUrl = userServiceUrl;
        this.driverServiceUrl = driverServiceUrl;
    }

    @Override
    public JsonNode lookup(AuthUserType type, String id) {
        if (id == null) {
            return null;
        }
        return switch (type) {
            case ADMIN -> lookupAdmin(id);
            case USER -> lookupUser(id);
            case DRIVER -> lookupDriver(id);
            default -> null;
        };
    }

    private JsonNode lookupAdmin(String id) {
        UUID adminId;
        try {
            adminId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
        // Original authMiddleware only checks the admin exists (no isactive filter here -
        // isactive is only consulted by login_admin's own business logic), so preserve that.
        return adminRepository.findById(adminId).map(this::toPrincipalJson).orElse(null);
    }

    private JsonNode toPrincipalJson(Admin admin) {
        // Mirrors getAdmin()'s Mongoose projection: "_id fullname email password profile mobileno".
        ObjectNode node = objectMapper.createObjectNode();
        node.put("_id", admin.getId().toString());
        node.put("fullname", admin.getFullname());
        node.put("email", admin.getEmail());
        node.put("password", admin.getPassword());
        node.put("profile", admin.getProfile());
        node.put("mobileno", admin.getMobileno());
        return node;
    }

    private JsonNode lookupUser(String id) {
        JsonNode response = internalServiceClient.get(userServiceUrl + "/profile?userId=" + id);
        if (response == null || !internalServiceClient.statusTrue(response)) {
            return null;
        }
        JsonNode user = response.path("data").path("user");
        return user.isMissingNode() || user.isNull() ? null : user;
    }

    private JsonNode lookupDriver(String id) {
        JsonNode response = internalServiceClient.get(driverServiceUrl + "/driver-details/" + id);
        if (response == null || !internalServiceClient.statusTrue(response)) {
            return null;
        }
        JsonNode driver = response.path("data");
        return driver.isMissingNode() || driver.isNull() ? null : driver;
    }
}
