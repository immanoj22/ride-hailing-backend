package com.ridehail.driverservice.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
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
 * Equivalent of the legacy {@code uploadImageToS3}/{@code uploadImage} helpers
 * (helpers/driver.commonFunction.js + utills/driver.axios.js): forwards uploaded multipart files
 * to common-service's {@code POST /common-apis/image-upload} (always {@code allSame=true} with a
 * fixed {@code sameFolderName}, one {@code subFolderArr} entry per file), exactly like
 * {@code uploadImageToS3(file, sameFolderName, allSame)} does. {@link com.ridehail.common.security.InternalServiceClient}
 * isn't reused here because it only supports JSON bodies, not multipart file uploads.
 */
@Component
public class DriverImageUploadClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String commonServiceUrl;
    private final String inAppSecret;
    private final String encSecret;
    private final boolean plaintextEnv;

    public DriverImageUploadClient(
            RestTemplateBuilder builder,
            @Value("${ridehail.services.common-url}") String commonServiceUrl,
            @Value("${ridehail.jwt.in-app-secret:}") String inAppSecret,
            @Value("${ridehail.enc-secret:}") String encSecret,
            @Value("${ridehail.env:development}") String env) {
        this.restTemplate = builder.build();
        this.commonServiceUrl = commonServiceUrl;
        this.inAppSecret = inAppSecret;
        this.encSecret = encSecret;
        this.plaintextEnv = "development".equalsIgnoreCase(env) || "demo".equalsIgnoreCase(env);
    }

    /** Uploads every file to the same {@code public_driver} folder, one sub-folder per file. */
    public List<String> uploadToPublicDriver(List<MultipartFile> files, List<String> subFolders) {
        return upload(files, subFolders, "public_driver");
    }

    public List<String> upload(List<MultipartFile> files, List<String> subFolders, String sameFolderName) {
        List<String> urls = new ArrayList<>();
        if (files == null || files.isEmpty()) {
            return urls;
        }
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                body.add("imageFiles", toResource(file));
            }
            if (subFolders != null) {
                for (String subFolder : subFolders) {
                    body.add("subFolderArr", subFolder == null ? "" : subFolder);
                }
            }
            body.add("allSame", "true");
            body.add("sameFolderName", sameFolderName);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("user-auth-type", "ridehail-in-app");
            headers.set("X-Internal-Secret", inAppSecret);

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
            String rawBody = restTemplate.postForObject(commonServiceUrl + "/image-upload", entity, String.class);
            JsonNode response = parse(rawBody);
            if (response != null && response.path("data").isArray()) {
                response.path("data").forEach(node -> urls.add(node.asText()));
            }
        } catch (Exception e) {
            // Mirrors uploadImageToS3's catch-and-return-null: upload failures never bubble up as
            // hard errors, callers just see no urls for the affected document/photo.
        }
        return urls;
    }

    private JsonNode parse(String rawBody) {
        try {
            if (rawBody == null || rawBody.isBlank()) {
                return null;
            }
            String json = plaintextEnv ? rawBody : CryptoJsCompatibleCipher.decrypt(rawBody, encSecret);
            return json == null ? null : objectMapper.readTree(json);
        } catch (IOException e) {
            return null;
        }
    }

    private ByteArrayResource toResource(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            return new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
        } catch (IOException e) {
            throw new com.ridehail.common.exception.ApiException(500, "Failed to read uploaded file");
        }
    }
}
