package com.ridehail.common.security;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehail.common.crypto.CryptoJsCompatibleCipher;

/**
 * Equivalent of utils/*.axios.js: every service-to-service call carries {@code user-auth-type:
 * ridehail-in-app} + {@code X-Internal-Secret}. The response body itself is still subject to the
 * same env-based encryption as any other API response (the legacy {@code sendResponse} only
 * branches on NODE_ENV, not on caller identity) so this client transparently decrypts it before
 * handing back parsed JSON, mirroring what {@code axiosFunc} callers received.
 */
@Component
public class InternalServiceClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String inAppSecret;
    private final String encSecret;
    private final boolean plaintextEnv;

    public InternalServiceClient(
            RestTemplateBuilder builder,
            @Value("${ridehail.jwt.in-app-secret:}") String inAppSecret,
            @Value("${ridehail.enc-secret:}") String encSecret,
            @Value("${ridehail.env:development}") String env) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
        this.inAppSecret = inAppSecret;
        this.encSecret = encSecret;
        this.plaintextEnv = "development".equalsIgnoreCase(env) || "demo".equalsIgnoreCase(env);
    }

    public JsonNode get(String url) {
        return exchange(url, HttpMethod.GET, null);
    }

    public JsonNode post(String url, Object body) {
        return exchange(url, HttpMethod.POST, body);
    }

    private JsonNode exchange(String url, HttpMethod method, Object body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("user-auth-type", "ridehail-in-app");
            headers.set("X-Internal-Secret", inAppSecret);
            HttpEntity<Object> entity = new HttpEntity<>(body, headers);

            String rawBody = restTemplate.exchange(url, method, entity, String.class).getBody();
            if (rawBody == null || rawBody.isBlank()) {
                return objectMapper.createObjectNode();
            }
            String json = plaintextEnv ? rawBody : CryptoJsCompatibleCipher.decrypt(rawBody, encSecret);
            if (json == null) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(json);
        } catch (RestClientException | java.io.IOException e) {
            return objectMapper.createObjectNode().put("success", false).put("message", "Service communication failed");
        }
    }

    /** Convenience for the very common "does the id exist and is it active" lookup pattern. */
    public boolean statusTrue(JsonNode response) {
        return response != null && response.path("status").asBoolean(false);
    }

    public Map<String, Object> asMap(JsonNode node) {
        return objectMapper.convertValue(node, Map.class);
    }
}
