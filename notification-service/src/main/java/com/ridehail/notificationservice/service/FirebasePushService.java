package com.ridehail.notificationservice.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;

/**
 * Port of helper/pushnotification.js's {@code buildMulticastPayload} + the FCM-send portion of
 * {@code SendNotification} (title/body resolution and the outstation channel-id logic there are
 * driven from ride-service's own notification payloads, not this bucket's admin-broadcast path,
 * so only the generic title/body/image case is reproduced here).
 */
@Service
public class FirebasePushService {

    private static final Logger log = LoggerFactory.getLogger(FirebasePushService.class);
    private static final String FALLBACK_IMAGE = "https://cdn-icons-png.flaticon.com/512/1827/1827272.png";

    private final ObjectProvider<FirebaseMessaging> firebaseMessaging;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FirebasePushService(ObjectProvider<FirebaseMessaging> firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
    }

    public boolean isEnabled() {
        return firebaseMessaging.getIfAvailable() != null;
    }

    /** Mirrors normalizeAndFilterTokens: trim, keep only strings &gt;50 chars without "sample". */
    public List<String> normalizeTokens(List<String> tokens) {
        List<String> result = new ArrayList<>();
        if (tokens == null) {
            return result;
        }
        for (String token : tokens) {
            if (token == null) {
                continue;
            }
            String trimmed = token.trim();
            if (trimmed.length() > 50 && !trimmed.toLowerCase().contains("sample")) {
                result.add(trimmed);
            }
        }
        return result;
    }

    public void sendMulticast(List<String> tokens, String title, String body, String image, String from) {
        FirebaseMessaging messaging = firebaseMessaging.getIfAvailable();
        if (messaging == null || tokens == null || tokens.isEmpty()) {
            return;
        }
        String finalImage = (image == null || image.isBlank()) ? FALLBACK_IMAGE : image;
        String resolvedTitle = (title == null || title.isBlank()) ? "Notification" : title;
        String resolvedBody = body == null ? "" : body;

        String dataJson;
        try {
            dataJson = objectMapper.writeValueAsString(java.util.Map.of(
                    "title", resolvedTitle,
                    "body", resolvedBody,
                    "imageUrl", finalImage,
                    "from", from,
                    "showlp", true));
        } catch (JsonProcessingException e) {
            dataJson = "{}";
        }

        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder()
                        .setTitle(resolvedTitle)
                        .setBody(resolvedBody)
                        .setImage(finalImage)
                        .build())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder()
                                .setChannelId("default")
                                .setImage(finalImage)
                                .setSound("default")
                                .build())
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .setAps(Aps.builder().setMutableContent(true).setSound("default").build())
                        .putCustomData("fcm_options", java.util.Map.of("image", finalImage))
                        .build())
                .putData("data", dataJson)
                .build();

        try {
            var response = messaging.sendEachForMulticast(message);
            log.info("FCM multicast ({}): {} success, {} failure", from, response.getSuccessCount(), response.getFailureCount());
        } catch (Exception e) {
            log.error("FCM multicast send failed ({})", from, e);
        }
    }
}
