package com.ridehail.socketservice.security;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.corundumstudio.socketio.AuthorizationListener;
import com.corundumstudio.socketio.AuthorizationResult;
import com.corundumstudio.socketio.HandshakeData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehail.common.constants.AuthUserType;
import com.ridehail.common.crypto.CryptoJsCompatibleCipher;
import com.ridehail.common.security.InternalServiceClient;
import com.ridehail.common.security.JwtService;
import com.ridehail.socketservice.util.SocketConstants;

/**
 * Port of middleware/socket.auth.middleware.js -> socketAuthMiddleware. Runs at the Socket.IO
 * handshake: resolves user-auth-type + token, verifies the JWT against the matching legacy
 * secret, looks the principal up in the owning service, and stashes userId/userType/userData in
 * the client store (via {@link AuthorizationResult} storeParams) for the event handlers.
 */
@Component
public class SocketHandshakeAuthListener implements AuthorizationListener {

    private static final Logger log = LoggerFactory.getLogger(SocketHandshakeAuthListener.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final JwtService jwtService;
    private final InternalServiceClient internalServiceClient;
    private final String userServiceUrl;
    private final String driverServiceUrl;
    private final String adminServiceUrl;
    private final String userSecret;
    private final String driverSecret;
    private final String adminSecret;
    private final String inAppSecret;
    private final String otherSecret;
    private final String encSecret;

    public SocketHandshakeAuthListener(
            JwtService jwtService,
            InternalServiceClient internalServiceClient,
            @Value("${ridehail.services.user-url}") String userServiceUrl,
            @Value("${ridehail.services.driver-url}") String driverServiceUrl,
            @Value("${ridehail.services.admin-url}") String adminServiceUrl,
            @Value("${ridehail.jwt.user-secret:}") String userSecret,
            @Value("${ridehail.jwt.driver-secret:}") String driverSecret,
            @Value("${ridehail.jwt.admin-secret:}") String adminSecret,
            @Value("${ridehail.jwt.in-app-secret:}") String inAppSecret,
            @Value("${ridehail.jwt.other-secret:}") String otherSecret,
            @Value("${ridehail.enc-secret:}") String encSecret) {
        this.jwtService = jwtService;
        this.internalServiceClient = internalServiceClient;
        this.userServiceUrl = userServiceUrl;
        this.driverServiceUrl = driverServiceUrl;
        this.adminServiceUrl = adminServiceUrl;
        this.userSecret = userSecret;
        this.driverSecret = driverSecret;
        this.adminSecret = adminSecret;
        this.inAppSecret = inAppSecret;
        this.otherSecret = otherSecret;
        this.encSecret = encSecret;
    }

    @Override
    public AuthorizationResult getAuthorizationResult(HandshakeData data) {
        try {
            Map<String, Object> auth = extractAuth(data);

            String userAuthType = header(data, "user-auth-type");
            if (isEmpty(userAuthType) && auth.get("userAuthType") != null) {
                userAuthType = String.valueOf(auth.get("userAuthType"));
            }

            String token = resolveToken(auth, data);
            String internalSecret = header(data, "x-internal-secret");
            if (isEmpty(internalSecret) && auth.get("internalSecret") != null) {
                internalSecret = String.valueOf(auth.get("internalSecret"));
            }

            AuthUserType authUser = AuthUserType.fromHeaderValue(userAuthType).orElse(null);

            if (isEmpty(userAuthType) || authUser == null) {
                return AuthorizationResult.FAILED_AUTHORIZATION;
            }
            if (authUser != AuthUserType.IN_APP && isEmpty(token)) {
                return AuthorizationResult.FAILED_AUTHORIZATION;
            }

            Map<String, Object> store = new HashMap<>();
            store.put(SocketConstants.STORE_USER_TYPE, mapType(authUser));

            switch (authUser) {
                case USER -> {
                    Map<String, Object> claims = jwtService.verify(token, userSecret);
                    if (claims == null) {
                        return AuthorizationResult.FAILED_AUTHORIZATION;
                    }
                    JsonNode resp = internalServiceClient.get(userServiceUrl + "/profile/" + claims.get("id"));
                    if (resp == null || !resp.path("status").asBoolean(false)) {
                        return AuthorizationResult.FAILED_AUTHORIZATION;
                    }
                    JsonNode user = resp.path("data").path("user");
                    store.put(SocketConstants.STORE_USER_ID, text(user.path("_id")));
                    store.put(SocketConstants.STORE_USER_DATA, toMap(user));
                }
                case DRIVER -> {
                    Map<String, Object> claims = jwtService.verify(token, driverSecret);
                    if (claims == null) {
                        return AuthorizationResult.FAILED_AUTHORIZATION;
                    }
                    JsonNode resp = internalServiceClient.get(driverServiceUrl + "/profile/" + claims.get("id"));
                    if (resp == null) {
                        return AuthorizationResult.FAILED_AUTHORIZATION;
                    }
                    JsonNode driver = resp.has("data") ? resp.path("data") : resp;
                    store.put(SocketConstants.STORE_USER_ID, text(driver.path("_id")));
                    store.put(SocketConstants.STORE_USER_DATA, toMap(driver));
                }
                case ADMIN -> {
                    Map<String, Object> claims = jwtService.verify(token, adminSecret);
                    if (claims == null) {
                        return AuthorizationResult.FAILED_AUTHORIZATION;
                    }
                    JsonNode resp = internalServiceClient.get(adminServiceUrl + "/get-admin/" + claims.get("id"));
                    if (resp == null || !resp.path("status").asBoolean(false)) {
                        return AuthorizationResult.FAILED_AUTHORIZATION;
                    }
                    store.put(SocketConstants.STORE_USER_ID, "admin");
                    store.put(SocketConstants.STORE_USER_DATA, toMap(resp.path("data")));
                }
                case OTHER -> {
                    JsonNode decoded = decryptOther(token);
                    if (decoded == null || !otherSecret.equals(decoded.path("secret").asText(null))) {
                        return AuthorizationResult.FAILED_AUTHORIZATION;
                    }
                    String deadline = decoded.path("deadline").asText(null);
                    if (isEmpty(deadline)) {
                        return AuthorizationResult.FAILED_AUTHORIZATION;
                    }
                    try {
                        long dl = Long.parseLong(deadline);
                        if (dl < System.currentTimeMillis()) {
                            return AuthorizationResult.FAILED_AUTHORIZATION;
                        }
                    } catch (NumberFormatException nfe) {
                        if (java.time.Instant.parse(deadline).toEpochMilli() < System.currentTimeMillis()) {
                            return AuthorizationResult.FAILED_AUTHORIZATION;
                        }
                    }
                }
                case IN_APP -> {
                    if (isEmpty(internalSecret) || !internalSecret.equals(inAppSecret)) {
                        return AuthorizationResult.FAILED_AUTHORIZATION;
                    }
                }
                default -> {
                    return AuthorizationResult.FAILED_AUTHORIZATION;
                }
            }

            return new AuthorizationResult(true, store);
        } catch (Exception e) {
            log.error("Socket auth error", e);
            return AuthorizationResult.FAILED_AUTHORIZATION;
        }
    }

    private String mapType(AuthUserType t) {
        return switch (t) {
            case USER -> "user";
            case DRIVER -> "driver";
            case ADMIN -> "admin";
            case OTHER -> "other";
            case IN_APP -> "inapp";
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractAuth(HandshakeData data) {
        Object authToken = data.getAuthToken();
        if (authToken instanceof Map) {
            return (Map<String, Object>) authToken;
        }
        return new HashMap<>();
    }

    private String resolveToken(Map<String, Object> auth, HandshakeData data) {
        Object t = auth.get("token");
        if (t != null) {
            String s = String.valueOf(t);
            String[] parts = s.split(" ");
            if (parts.length > 1) {
                return parts[1];
            }
            return s;
        }
        String authorization = header(data, "authorization");
        if (!isEmpty(authorization)) {
            String[] parts = authorization.split(" ");
            return parts.length > 1 ? parts[1] : parts[0];
        }
        return "";
    }

    private JsonNode decryptOther(String token) {
        try {
            String base64Decoded = new String(Base64.getDecoder().decode(token));
            String json = CryptoJsCompatibleCipher.decrypt(base64Decoded, encSecret);
            return json == null ? null : objectMapper.readTree(json);
        } catch (Exception e) {
            try {
                String json = CryptoJsCompatibleCipher.decrypt(token, encSecret);
                return json == null ? null : objectMapper.readTree(json);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private String header(HandshakeData data, String name) {
        List<String> values = data.getHttpHeaders() == null ? null : data.getHttpHeaders().getAll(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private Map<String, Object> toMap(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return objectMapper.convertValue(node, Map.class);
    }

    private String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private boolean isEmpty(String v) {
        return v == null || v.trim().isEmpty() || "0".equals(v) || "undefined".equals(v) || "null".equals(v);
    }
}
