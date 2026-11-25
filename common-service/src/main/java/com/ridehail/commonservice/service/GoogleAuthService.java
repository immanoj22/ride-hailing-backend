package com.ridehail.commonservice.service;

import java.util.Collections;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.ridehail.common.exception.ApiException;

/**
 * Direct port of {@code verifyGoogleToken} (controllers/common.controller.js), using Google's
 * Java auth library equivalent of {@code google-auth-library}'s {@code OAuth2Client.verifyIdToken}.
 */
@Service
public class GoogleAuthService {

    private final GoogleIdTokenVerifier verifier;

    public GoogleAuthService(@Value("${ridehail.google.web-client-id}") String webClientId) throws Exception {
        this.verifier = new GoogleIdTokenVerifier.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(webClientId))
                .build();
    }

    public Map<String, Object> verify(String token) {
        try {
            GoogleIdToken idToken = verifier.verify(token);
            if (idToken == null) {
                throw new ApiException(401, "Email not verified.");
            }
            GoogleIdToken.Payload payload = idToken.getPayload();
            if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
                throw new ApiException(401, "Email not verified.");
            }
            return Map.of(
                    "email", payload.getEmail(),
                    "name", String.valueOf(payload.get("name")),
                    "picture", String.valueOf(payload.get("picture")));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(401, "Email not verified.");
        }
    }
}
