package com.ridehail.notificationservice.config;

import java.io.FileInputStream;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import jakarta.annotation.Nullable;

/**
 * Replaces helper/pushnotification.js's {@code admin.initializeApp({credential: cert(serviceAccount)})}.
 * The legacy code loaded a checked-in {@code firebase-credentials.json}; here the path comes from
 * config so the key can be mounted/rotated. When no path is configured the bean is null and
 * {@link com.ridehail.notificationservice.service.FirebasePushService} degrades to a no-op instead of
 * failing service startup.
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Bean
    @Nullable
    public FirebaseMessaging firebaseMessaging(@Value("${spring.firebase.credentials-path:}") String credentialsPath) {
        if (credentialsPath == null || credentialsPath.isBlank()) {
            log.warn("Firebase credentials path not configured - push notifications are disabled.");
            return null;
        }
        try (InputStream serviceAccount = new FileInputStream(credentialsPath.trim())) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();
            FirebaseApp app = FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(options)
                    : FirebaseApp.getInstance();
            return FirebaseMessaging.getInstance(app);
        } catch (Exception e) {
            log.error("Failed to initialize Firebase from {} - push notifications disabled.", credentialsPath, e);
            return null;
        }
    }
}
