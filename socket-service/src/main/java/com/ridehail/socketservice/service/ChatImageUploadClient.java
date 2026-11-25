package com.ridehail.socketservice.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehail.common.crypto.CryptoJsCompatibleCipher;

/**
 * Equivalent of helper/socket.commonFunction.js#uploadImageToS3 + utills/socket.axios.js#uploadImage:
 * multipart POST to {@code COMMON_SERVICE_URL/image-upload} with the {@code user-auth-type:
 * ridehail-in-app} + {@code X-Internal-Secret} headers, returning the first uploaded file URL
 * ({@code response.data[0]}) or {@code null} on failure. Kept separate from
 * {@link com.ridehail.common.security.InternalServiceClient} because that client is JSON-only.
 */
@Component
public class ChatImageUploadClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String commonServiceUrl;
    private final String inAppSecret;
    private final String encSecret;
    private final boolean plaintextEnv;

    public ChatImageUploadClient(
            @Value("${ridehail.services.common-url}") String commonServiceUrl,
            @Value("${ridehail.jwt.in-app-secret:}") String inAppSecret,
            @Value("${ridehail.enc-secret:}") String encSecret,
            @Value("${ridehail.env:development}") String env) {
        this.commonServiceUrl = commonServiceUrl;
        this.inAppSecret = inAppSecret;
        this.encSecret = encSecret;
        this.plaintextEnv = "development".equalsIgnoreCase(env) || "demo".equalsIgnoreCase(env);
    }

    /**
     * @param file      the uploaded chat attachment
     * @param subFolder {@code `${from}/${rideId}`} as built by the original
     * @param sameFolderName IMAGE_FOLDERS.public_chat
     * @return the stored file URL, or {@code null} if the upload failed
     */
    public String upload(MultipartFile file, String subFolder, String sameFolderName) {
        try {
            byte[] bytes = file.getBytes();
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
            ByteArrayResource resource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("imageFiles", resource);
            body.add("subFolderArr", subFolder != null ? subFolder : "");
            body.add("allSame", "true");
            if (sameFolderName != null) {
                body.add("sameFolderName", sameFolderName);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("user-auth-type", "ridehail-in-app");
            headers.set("X-Internal-Secret", inAppSecret);

            String raw = restTemplate.postForEntity(commonServiceUrl + "/image-upload",
                    new HttpEntity<>(body, headers), String.class).getBody();
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String json = plaintextEnv ? raw : CryptoJsCompatibleCipher.decrypt(raw, encSecret);
            if (json == null) {
                return null;
            }
            JsonNode response = objectMapper.readTree(json);
            if (!response.path("status").asBoolean(false)) {
                return null;
            }
            JsonNode data = response.path("data");
            if (data.isArray() && data.size() > 0) {
                return data.get(0).asText(null);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, Object> asMap(JsonNode node) {
        return objectMapper.convertValue(node, Map.class);
    }
}
