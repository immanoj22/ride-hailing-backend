package com.ridehail.common.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
 * Direct port of the legacy {@code decryptionMiddleware}: when the caller is not "inapp" and the
 * environment isn't development/demo, an {@code encData} field in the JSON body or a query
 * parameter replaces the whole body/query with its decrypted {@code reqData}. MUST run after
 * {@link RideHailAuthFilter} in the chain (it reads {@link AuthContext}, which that filter sets).
 */
public class EncryptedRequestFilter extends OncePerRequestFilter {

    private final ApiResponseService apiResponseService;
    private final String encSecret;
    private final boolean plaintextEnv;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EncryptedRequestFilter(ApiResponseService apiResponseService, String encSecret, String env) {
        this.apiResponseService = apiResponseService;
        this.encSecret = encSecret;
        this.plaintextEnv = "development".equalsIgnoreCase(env) || "demo".equalsIgnoreCase(env);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        AuthContext ctx = AuthContext.current();
        boolean isInApp = ctx != null && ctx.authUserType() == AuthUserType.IN_APP;

        if (plaintextEnv || isInApp) {
            chain.doFilter(request, response);
            return;
        }

        try {
            Map<String, Object> decryptedQuery = null;
            String encDataParam = request.getParameter("encData");
            if (encDataParam != null && !encDataParam.isBlank()) {
                decryptedQuery = extractReqData(encDataParam);
            }

            byte[] overrideBody = null;
            if ("POST".equalsIgnoreCase(request.getMethod())
                    || "PUT".equalsIgnoreCase(request.getMethod())
                    || "PATCH".equalsIgnoreCase(request.getMethod())) {
                byte[] originalBody = request.getInputStream().readAllBytes();
                if (originalBody.length > 0) {
                    JsonNode node = tryParseJson(originalBody);
                    if (node != null && node.hasNonNull("encData")) {
                        Map<String, Object> reqData = extractReqData(node.get("encData").asText());
                        overrideBody = objectMapper.writeValueAsBytes(reqData == null ? Map.of() : reqData);
                    } else {
                        overrideBody = originalBody; // pass through unchanged, just re-buffered
                    }
                }
            }

            HttpServletRequest effectiveRequest =
                    (decryptedQuery != null || overrideBody != null)
                            ? new DecryptedHttpServletRequestWrapper(request, overrideBody, decryptedQuery)
                            : request;

            chain.doFilter(effectiveRequest, response);
        } catch (Exception e) {
            apiResponseService.write(response, false, StatusResult.ERROR, StatusCode.BAD_REQUEST, "Data decryption error");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractReqData(String encData) throws IOException {
        String decrypted = CryptoJsCompatibleCipher.decrypt(encData, encSecret);
        if (decrypted == null) {
            return Map.of();
        }
        Map<String, Object> parsed = objectMapper.readValue(decrypted, Map.class);
        Object reqData = parsed.get("reqData");
        return reqData instanceof Map ? (Map<String, Object>) reqData : Map.of();
    }

    private JsonNode tryParseJson(byte[] bytes) {
        try {
            return objectMapper.readTree(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }
}
