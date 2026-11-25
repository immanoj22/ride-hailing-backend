package com.ridehail.adminservice.util;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehail.common.crypto.CryptoJsCompatibleCipher;

/**
 * multipart/form-data equivalent of {@link com.ridehail.common.security.InternalServiceClient} -
 * needed for the two legacy admin flows that forwarded a {@code FormData} body downstream:
 *   - coupon image upload  (helpers/admin.commonFunction.js#uploadImageToS3 -> common-service /image-upload)
 *   - push notification    (controller/admin.controller.js#push_notification -> notification-service /send-push-notification)
 * Sends the same {@code user-auth-type: ridehail-in-app} + {@code X-Internal-Secret} headers and
 * transparently decrypts the (possibly AES-encrypted) response body, exactly like axiosFunc callers received.
 */
@Component
public class InternalMultipartClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String inAppSecret;
    private final String encSecret;
    private final boolean plaintextEnv;

    public InternalMultipartClient(
            RestTemplateBuilder builder,
            @Value("${ridehail.jwt.in-app-secret:}") String inAppSecret,
            @Value("${ridehail.enc-secret:}") String encSecret,
            @Value("${ridehail.env:development}") String env) {
        this.restTemplate = builder.build();
        this.inAppSecret = inAppSecret;
        this.encSecret = encSecret;
        this.plaintextEnv = "development".equalsIgnoreCase(env) || "demo".equalsIgnoreCase(env);
    }

    public JsonNode post(String url, MultiValueMap<String, Object> form) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("user-auth-type", "ridehail-in-app");
            headers.set("X-Internal-Secret", inAppSecret);
            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(form, headers);

            String rawBody = restTemplate.exchange(url, HttpMethod.POST, entity, String.class).getBody();
            if (rawBody == null || rawBody.isBlank()) {
                return objectMapper.createObjectNode();
            }
            String json = plaintextEnv ? rawBody : CryptoJsCompatibleCipher.decrypt(rawBody, encSecret);
            if (json == null) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(json);
        } catch (RestClientException | IOException e) {
            return objectMapper.createObjectNode().put("status", false).put("message", "Service communication failed");
        }
    }

    /** Wraps a {@link MultipartFile} as a filename-carrying resource part. */
    public static ByteArrayResource filePart(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            return new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }

                @Override
                public long contentLength() {
                    return bytes.length;
                }
            };
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read upload", e);
        }
    }

    public static MultiValueMap<String, Object> newForm() {
        return new LinkedMultiValueMap<>();
    }
}
