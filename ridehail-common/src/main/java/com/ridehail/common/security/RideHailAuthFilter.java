package com.ridehail.common.security;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehail.common.constants.AuthUserType;
import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.constants.StatusResult;
import com.ridehail.common.crypto.CryptoJsCompatibleCipher;
import com.ridehail.common.response.ApiResponseService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Direct port of the legacy {@code authMiddleware} (common-service/middlewares/common.auth.middleware.js
 * and every service's own copy of it): reads the {@code user-auth-type} header to pick a JWT
 * secret / verification path, and populates {@link AuthContext} for downstream controllers -
 * equivalent to the old {@code req.user}/{@code req.driver}/{@code req.admin}/{@code req.auth_user}.
 */
public class RideHailAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ApiResponseService apiResponseService;
    private final PrincipalLookupService principalLookupService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String userSecret;
    private final String driverSecret;
    private final String adminSecret;
    private final String otherSecret;
    private final String inAppSecret;

    public RideHailAuthFilter(
            JwtService jwtService,
            ApiResponseService apiResponseService,
            PrincipalLookupService principalLookupService,
            String userSecret,
            String driverSecret,
            String adminSecret,
            String otherSecret,
            String inAppSecret) {
        this.jwtService = jwtService;
        this.apiResponseService = apiResponseService;
        this.principalLookupService = principalLookupService;
        this.userSecret = userSecret;
        this.driverSecret = driverSecret;
        this.adminSecret = adminSecret;
        this.otherSecret = otherSecret;
        this.inAppSecret = inAppSecret;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String headerType = request.getHeader("user-auth-type");
            String authHeader = request.getHeader("authorization");
            String token = authHeader != null && authHeader.contains(" ")
                    ? authHeader.substring(authHeader.indexOf(' ') + 1)
                    : null;
            String internalSecret = request.getHeader("x-internal-secret");

            if (isBlank(headerType)) {
                unauthorized(response, StatusCode.BAD_REQUEST, "Header not found - 'user-auth-type'");
                return;
            }
            var maybeType = AuthUserType.fromHeaderValue(headerType);
            if (maybeType.isEmpty()) {
                unauthorized(response, StatusCode.BAD_REQUEST, "Not a valid user type - Auth");
                return;
            }
            AuthUserType type = maybeType.get();
            if (type != AuthUserType.IN_APP && isBlank(token)) {
                unauthorized(response, StatusCode.BAD_REQUEST, "Header not found - 'authorization'");
                return;
            }

            switch (type) {
                case USER -> {
                    if (!authenticatePrincipal(response, type, token, userSecret, "user")) return;
                }
                case DRIVER -> {
                    if (!authenticatePrincipal(response, type, token, driverSecret, "driver")) return;
                }
                case ADMIN -> {
                    if (!authenticatePrincipal(response, type, token, adminSecret, "admin")) return;
                }
                case OTHER -> {
                    String decrypted = CryptoJsCompatibleCipher.decrypt(token, otherSecret);
                    if (decrypted == null) {
                        unauthorized(response, StatusCode.UNAUTHORIZED, "Not an authorised user - Wrong auth key");
                        return;
                    }
                    Map<?, ?> data = objectMapper.readValue(decrypted, Map.class);
                    Object secretField = data.get("secret");
                    Object deadline = data.get("deadline");
                    if (secretField == null || !secretField.equals(otherSecret)) {
                        unauthorized(response, StatusCode.UNAUTHORIZED, "Not an authorised user - Wrong auth key");
                        return;
                    }
                    if (deadline == null) {
                        unauthorized(response, StatusCode.UNAUTHORIZED, "Not an authorised user - Deadline not found");
                        return;
                    }
                    if (Instant.parse(deadline.toString()).isBefore(Instant.now())) {
                        unauthorized(response, StatusCode.UNAUTHORIZED, "Not an authorised user - Token expired");
                        return;
                    }
                    AuthContext.set(AuthUserType.OTHER, null, null);
                }
                case IN_APP -> {
                    if (isBlank(internalSecret) || !internalSecret.equals(inAppSecret)) {
                        unauthorized(response, StatusCode.FORBIDDEN, "Forbidden - Invalid internal secret");
                        return;
                    }
                    AuthContext.set(AuthUserType.IN_APP, null, null);
                }
            }

            chain.doFilter(request, response);
        } catch (Exception e) {
            unauthorized(response, StatusCode.INTERNAL_SERVER_ERROR, "Authentication error");
        } finally {
            AuthContext.clear();
        }
    }

    private boolean authenticatePrincipal(HttpServletResponse response, AuthUserType type, String token, String secret, String label)
            throws IOException {
        Map<String, Object> claims = jwtService.verify(token, secret);
        if (claims == null) {
            unauthorized(response, StatusCode.UNAUTHORIZED, "Not an authorised " + label);
            return false;
        }
        Object id = claims.get("id");
        JsonNode principal = principalLookupService.lookup(type, id == null ? null : id.toString());
        if (principal == null) {
            unauthorized(response, StatusCode.UNAUTHORIZED, "Not an authorised " + label + " - " + label + " not found");
            return false;
        }
        AuthContext.set(type, id == null ? null : id.toString(), principal);
        return true;
    }

    private void unauthorized(HttpServletResponse response, int statusCode, String message) {
        apiResponseService.write(response, false, StatusResult.ERROR, statusCode, message);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank() || "0".equals(s);
    }
}
