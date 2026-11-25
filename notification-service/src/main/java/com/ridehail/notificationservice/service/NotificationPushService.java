package com.ridehail.notificationservice.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.common.security.AuthContext;
import com.ridehail.common.security.InternalServiceClient;
import com.ridehail.notificationservice.dto.NotificationResponse;
import com.ridehail.notificationservice.dto.PushNotificationRequest;
import com.ridehail.notificationservice.entity.Notification;
import com.ridehail.notificationservice.repository.NotificationRepository;

/**
 * "push-notifications" bucket - direct port of controllers/notification.controller.js's
 * pushNotification / getNotification / getAllNotification, plus service/notification.servie.js's
 * saveNotification / findAllNotification.
 */
@Service
@Transactional(readOnly = true)
public class NotificationPushService {

    private final NotificationRepository notificationRepository;
    private final FirebasePushService firebasePushService;
    private final InternalServiceClient internalServiceClient;
    private final ObjectMapper objectMapper;
    private final String userServiceUrl;
    private final String driverServiceUrl;

    public NotificationPushService(
            NotificationRepository notificationRepository,
            FirebasePushService firebasePushService,
            InternalServiceClient internalServiceClient,
            ObjectMapper objectMapper,
            @Value("${ridehail.services.user-url}") String userServiceUrl,
            @Value("${ridehail.services.driver-url}") String driverServiceUrl) {
        this.notificationRepository = notificationRepository;
        this.firebasePushService = firebasePushService;
        this.internalServiceClient = internalServiceClient;
        this.objectMapper = objectMapper;
        this.userServiceUrl = userServiceUrl;
        this.driverServiceUrl = driverServiceUrl;
    }

    // ---------------------------------------------------------------------------------------
    // POST /send-push-notification
    // ---------------------------------------------------------------------------------------

    @Transactional
    public void pushNotification(PushNotificationRequest request) {
        String whom = request.getWhom();
        List<String> userTokens = null;
        List<String> driverTokens = null;

        switch (whom == null ? "" : whom) {
            case "user" -> userTokens = fetchTokens(userServiceUrl + "/get-user-notifications", request.getMobile(), "all", "fcm");
            case "driver" -> driverTokens = fetchTokens(driverServiceUrl + "/get-drivers-for-notification", request.getMobile(), "all", "fcmToken");
            case "selectedUser" -> userTokens = fetchTokens(userServiceUrl + "/get-user-notifications", request.getMobile(), "individual", "fcm");
            case "selectedDriver" -> driverTokens = fetchTokens(driverServiceUrl + "/get-drivers-for-notification", request.getMobile(), "individual", "fcmToken");
            default -> { /* original: no lookup, tokens stay null */ }
        }

        String imageUrl = request.getImageUrl() == null ? "" : request.getImageUrl();

        // Matches the original's saveNotification call: mobile column stores JSON.stringify(user) ||
        // JSON.stringify(driver) - i.e. the users' tokens if present, else the drivers'.
        String mobileColumn = toJson(userTokens != null ? userTokens : driverTokens);
        Notification saved = Notification.builder()
                .title(orEmpty(request.getTitle()))
                .message(orEmpty(request.getMessage()))
                .image(imageUrl)
                .whom(orEmpty(whom))
                .mobile(mobileColumn)
                .coupons(orEmpty(request.getCoupon()))
                .build();
        notificationRepository.save(saved);

        if (userTokens != null) {
            firebasePushService.sendMulticast(firebasePushService.normalizeTokens(userTokens),
                    request.getTitle(), request.getMessage(), imageUrl, "user");
        }
        if (driverTokens != null) {
            firebasePushService.sendMulticast(firebasePushService.normalizeTokens(driverTokens),
                    request.getTitle(), request.getMessage(), imageUrl, "driver");
        }
    }

    private List<String> fetchTokens(String url, List<String> mobile, String action, String tokenField) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mobile", mobile);
        body.put("action", action);
        JsonNode resp = internalServiceClient.post(url, body);
        List<String> tokens = new ArrayList<>();
        if (resp != null && resp.path("data").isArray()) {
            for (JsonNode item : resp.path("data")) {
                String token = item.path(tokenField).asText(null);
                if (token != null && !token.isBlank()) {
                    tokens.add(token);
                }
            }
        }
        return tokens;
    }

    // ---------------------------------------------------------------------------------------
    // GET /get-notifications  (authMiddleware - so AuthContext carries a user OR driver)
    // ---------------------------------------------------------------------------------------

    public List<NotificationResponse> getNotification(String userQueryParam, int limit, int page) {
        AuthContext ctx = AuthContext.current();
        List<Notification> notifications;

        if (ctx != null && ctx.principal() != null && ctx.authUserType() == com.ridehail.common.constants.AuthUserType.USER) {
            UUID userId = uuidOrNull(ctx.principalId());
            String fcm = ctx.principal().path("fcm").asText("");
            notifications = notificationRepository.findForUser("user", fcm, userId);
        } else if (ctx != null && ctx.principal() != null && ctx.authUserType() == com.ridehail.common.constants.AuthUserType.DRIVER) {
            UUID driverId = uuidOrNull(ctx.principalId());
            String fcmToken = ctx.principal().path("fcmToken").asText("");
            notifications = notificationRepository.findForDriver("driver", fcmToken, driverId);
        } else {
            notifications = notificationRepository
                    .findByWhomOrderByCreatedAtDesc(userQueryParam, PageRequest.of(Math.max(page - 1, 0), Math.max(limit, 1)))
                    .getContent();
            return notifications.stream().map(NotificationResponse::from).toList();
        }

        // The native queries return the full ordered set; apply the original skip/limit in memory.
        int from = Math.max((page - 1) * limit, 0);
        int to = Math.min(from + limit, notifications.size());
        if (from >= notifications.size()) {
            return List.of();
        }
        return notifications.subList(from, to).stream().map(NotificationResponse::from).toList();
    }

    // ---------------------------------------------------------------------------------------
    // GET /get-all-notifications
    // ---------------------------------------------------------------------------------------

    public Map<String, Object> getAllNotification(Integer limit, Integer page) {
        List<Notification> notifications;
        long totalCount = notificationRepository.count();

        if (limit != null && page != null) {
            notifications = notificationRepository
                    .findAllByOrderByCreatedAtDesc(PageRequest.of(Math.max(page - 1, 0), Math.max(limit, 1)))
                    .getContent();
        } else {
            notifications = notificationRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Integer.MAX_VALUE)).getContent();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("notifications", notifications.stream().map(NotificationResponse::from).toList());
        result.put("totalCount", totalCount);
        return result;
    }

    // ---------------------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------------------

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private UUID uuidOrNull(String raw) {
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
