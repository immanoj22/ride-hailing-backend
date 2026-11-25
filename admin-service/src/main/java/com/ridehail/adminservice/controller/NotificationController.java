package com.ridehail.adminservice.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ridehail.adminservice.dto.SendPushNotificationRequest;
import com.ridehail.adminservice.service.NotificationService;
import com.ridehail.common.response.ApiResponseService;

import jakarta.validation.Valid;

/**
 * Direct port of the //notifcation routes in Admin-service/routes/admin.routes.js (mounted at
 * /admin-apis): push_notification + get_notification.
 */
@RestController
@RequestMapping("/admin-apis")
public class NotificationController {

    private final NotificationService notificationService;
    private final ApiResponseService apiResponseService;

    public NotificationController(NotificationService notificationService, ApiResponseService apiResponseService) {
        this.notificationService = notificationService;
        this.apiResponseService = apiResponseService;
    }

    @PostMapping(value = "/send-push-notification", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> pushNotification(@Valid @ModelAttribute SendPushNotificationRequest request,
                                              @RequestParam(value = "image", required = false) MultipartFile image) {
        notificationService.pushNotification(request, image);
        return apiResponseService.ok("Notification sent successfully", java.util.Map.of());
    }

    @GetMapping("/get_notification")
    public ResponseEntity<?> getNotification(@RequestParam(value = "search", required = false) String search,
                                             @RequestParam(value = "limit", required = false) String limit,
                                             @RequestParam(value = "page", required = false) String page) {
        return apiResponseService.ok("Notifications fetched successfully",
                notificationService.getNotifications(search, limit, page));
    }
}
