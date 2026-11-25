package com.ridehail.notificationservice.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ridehail.common.response.ApiResponseService;
import com.ridehail.notificationservice.dto.NotificationResponse;
import com.ridehail.notificationservice.dto.PushNotificationRequest;
import com.ridehail.notificationservice.service.NotificationPushService;

/**
 * "push-notifications" bucket - direct port of routes/notification.routes.js's
 * send-push-notification / get-notifications / get-all-notifications routes (mounted under
 * "/notification-apis" by server.js).
 */
@RestController
@RequestMapping("/notification-apis")
public class NotificationController {

    private final NotificationPushService notificationPushService;
    private final ApiResponseService apiResponseService;

    public NotificationController(NotificationPushService notificationPushService, ApiResponseService apiResponseService) {
        this.notificationPushService = notificationPushService;
        this.apiResponseService = apiResponseService;
    }

    /** POST /send-push-notification. */
    @PostMapping("/send-push-notification")
    public ResponseEntity<?> pushNotification(@RequestBody PushNotificationRequest request) {
        notificationPushService.pushNotification(request);
        return apiResponseService.ok("Notification sent successfully", Map.of());
    }

    /** GET /get-notifications. */
    @GetMapping("/get-notifications")
    public ResponseEntity<?> getNotification(
            @RequestParam(value = "user", required = false) String user,
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            @RequestParam(value = "page", defaultValue = "1") int page) {
        List<NotificationResponse> data = notificationPushService.getNotification(user, limit, page);
        return apiResponseService.ok("Notifications fetched successfully", data);
    }

    /** GET /get-all-notifications. */
    @GetMapping("/get-all-notifications")
    public ResponseEntity<?> getAllNotification(
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "page", required = false) Integer page) {
        Map<String, Object> data = notificationPushService.getAllNotification(limit, page);
        return apiResponseService.ok("Notifications fetched successfully", data);
    }
}
