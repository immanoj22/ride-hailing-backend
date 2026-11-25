package com.ridehail.adminservice.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehail.common.crypto.CryptoJsCompatibleCipher;

/**
 * Direct port of Admin-service/utills/admin.axios.js's {@code axiosFunc}: every service-to-service
 * call carries {@code user-auth-type: ridehail-in-app} + {@code X-Internal-Secret}, uses
 * {@code validateStatus: () => true} (never throws on non-2xx), a 10s timeout, and JSON by default.
 * The callee response body is still env-encrypted (the legacy {@code sendResponse} only branches on
 * NODE_ENV), so it is transparently decrypted here before being parsed - mirroring what the
 * original axios callers received.
 *
 * <p>{@link com.ridehail.common.security.InternalServiceClient} covers the GET/POST-JSON case; this
 * helper additionally supports query params, the PUT/PATCH/DELETE verbs the support/chat proxies
 * use, and raw {@code byte[]} bodies for the CSV/PDF export endpoints.</p>
 */
@Component
public class DownstreamClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String inAppSecret;
    private final String encSecret;
    private final boolean plaintextEnv;

    public DownstreamClient(
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
        return exchange(url, HttpMethod.GET, null, null);
    }

    public JsonNode get(String url, Map<String, ?> params) {
        return exchange(url + buildQuery(params), HttpMethod.GET, null, null);
    }

    public JsonNode post(String url, Object body) {
        return exchange(url, HttpMethod.POST, body, null);
    }

    public JsonNode put(String url, Object body) {
        return exchange(url, HttpMethod.PUT, body, null);
    }

    public JsonNode delete(String url, Map<String, ?> params) {
        return exchange(url + buildQuery(params), HttpMethod.DELETE, null, null);
    }

    public JsonNode patch(String url, Map<String, ?> params) {
        return exchange(url + buildQuery(params), HttpMethod.PATCH, null, null);
    }

    public JsonNode postMultipart(String url, org.springframework.util.MultiValueMap<String, Object> form) {
        return multipart(url, HttpMethod.POST, form);
    }

    public JsonNode putMultipart(String url, org.springframework.util.MultiValueMap<String, Object> form) {
        return multipart(url, HttpMethod.PUT, form);
    }

    private JsonNode multipart(String url, HttpMethod method, org.springframework.util.MultiValueMap<String, Object> form) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("user-auth-type", "ridehail-in-app");
            headers.set("X-Internal-Secret", inAppSecret);
            HttpEntity<Object> entity = new HttpEntity<>(form, headers);
            String rawBody = restTemplate.exchange(url, method, entity, String.class).getBody();
            if (rawBody == null || rawBody.isBlank()) {
                return objectMapper.createObjectNode();
            }
            String json = plaintextEnv ? rawBody : CryptoJsCompatibleCipher.decrypt(rawBody, encSecret);
            return json == null ? objectMapper.createObjectNode() : objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.createObjectNode()
                    .put("success", false)
                    .put("message", "Service communication failed")
                    .put("code", 500);
        }
    }

    /** Raw bytes for the export endpoints (axios {@code responseType: "arraybuffer"}). */
    public byte[] getBytes(String url, Map<String, ?> params) {
        try {
            HttpEntity<Object> entity = new HttpEntity<>(baseHeaders());
            return restTemplate.exchange(url + buildQuery(params), HttpMethod.GET, entity, byte[].class).getBody();
        } catch (Exception e) {
            return null;
        }
    }

    private HttpHeaders baseHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("user-auth-type", "ridehail-in-app");
        headers.set("X-Internal-Secret", inAppSecret);
        return headers;
    }

    private JsonNode exchange(String url, HttpMethod method, Object body, HttpHeaders extra) {
        try {
            HttpHeaders headers = baseHeaders();
            if (extra != null) {
                headers.putAll(extra);
            }
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
        } catch (Exception e) {
            // Mirrors axiosFunc's catch: { success:false, message:"Service communication failed", code:500 }
            return objectMapper.createObjectNode()
                    .put("success", false)
                    .put("message", "Service communication failed")
                    .put("code", 500);
        }
    }

    private String buildQuery(Map<String, ?> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, ?> e : params.entrySet()) {
            Object v = e.getValue();
            if (v == null) {
                continue;
            }
            String s = String.valueOf(v);
            if (s.isEmpty()) {
                continue;
            }
            sb.append(sb.length() == 0 ? '?' : '&')
              .append(enc(e.getKey())).append('=').append(enc(s));
        }
        return sb.toString();
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            return s;
        }
    }
}
