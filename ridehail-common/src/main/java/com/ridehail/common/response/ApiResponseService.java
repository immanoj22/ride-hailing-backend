package com.ridehail.common.response;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehail.common.crypto.CryptoJsCompatibleCipher;

/**
 * Equivalent of the legacy {@code sendResponse(res, status, success, statusCode, message, data)}
 * helper (common-service/config/common_functions.js). In development/demo the JSON envelope is
 * returned as-is; in every other environment the whole envelope is AES-encrypted (see
 * {@link CryptoJsCompatibleCipher}) and returned as a raw string body, exactly like the original.
 */
@Component
public class ApiResponseService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String env;
    private final String encSecret;

    public ApiResponseService(
            @Value("${ridehail.env:development}") String env,
            @Value("${ridehail.enc-secret:}") String encSecret) {
        this.env = env;
        this.encSecret = encSecret;
    }

    private boolean isPlaintextEnv() {
        return "development".equalsIgnoreCase(env) || "demo".equalsIgnoreCase(env);
    }

    public <T> ResponseEntity<?> send(boolean status, String success, int statusCode, String message, T data) {
        ApiResponse<T> envelope = new ApiResponse<>(status, message, data, success);
        if (isPlaintextEnv()) {
            return ResponseEntity.status(statusCode).contentType(MediaType.APPLICATION_JSON).body(envelope);
        }
        try {
            String json = objectMapper.writeValueAsString(envelope);
            String wire = CryptoJsCompatibleCipher.encrypt(json, encSecret);
            return ResponseEntity.status(statusCode).contentType(MediaType.TEXT_PLAIN).body(wire);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build encrypted response", e);
        }
    }

    public ResponseEntity<?> send(boolean status, String success, int statusCode, String message) {
        return send(status, success, statusCode, message, java.util.Map.of());
    }

    public <T> ResponseEntity<?> ok(String message, T data) {
        return send(true, com.ridehail.common.constants.StatusResult.SUCCESS, com.ridehail.common.constants.StatusCode.SUCCESS, message, data);
    }

    public ResponseEntity<?> error(int statusCode, String message) {
        return send(false, com.ridehail.common.constants.StatusResult.ERROR, statusCode, message);
    }

    /**
     * Decrypts a request payload wrapped as {@code encData} back into the raw JSON string the
     * original request body/query would have contained (the legacy {@code Decryptdata} helper
     * returns the full decrypted object, whose {@code reqData} field is the actual payload).
     */
    public String decryptRequestPayload(String encData) {
        return CryptoJsCompatibleCipher.decrypt(encData, encSecret);
    }

    public boolean isPlaintextEnvironment() {
        return isPlaintextEnv();
    }

    /** For use from servlet filters (auth/decryption), which run before Spring MVC and so can't
     *  simply return a ResponseEntity - they must write directly to the response. */
    public void write(jakarta.servlet.http.HttpServletResponse response, boolean status, String success, int statusCode, String message) {
        try {
            ApiResponse<Object> envelope = new ApiResponse<>(status, message, java.util.Map.of(), success);
            response.setStatus(statusCode);
            String json = objectMapper.writeValueAsString(envelope);
            if (isPlaintextEnv()) {
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(json);
            } else {
                response.setContentType(MediaType.TEXT_PLAIN_VALUE);
                response.getWriter().write(CryptoJsCompatibleCipher.encrypt(json, encSecret));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write error response", e);
        }
    }
}
