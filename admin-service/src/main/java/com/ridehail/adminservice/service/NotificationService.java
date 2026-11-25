package com.ridehail.adminservice.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehail.adminservice.entity.AdminActivity;
import com.ridehail.adminservice.repository.AdminActivityRepository;
import com.ridehail.adminservice.util.InternalMultipartClient;
import com.ridehail.common.constants.AuthUserType;
import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.common.security.AuthContext;
import com.ridehail.common.security.InternalServiceClient;

/**
 * Port of push_notification + get_notification (Admin-service/controller/admin.controller.js).
 * Both are thin proxies to notification-service (utills/admin.axios.js#sendPushNotification /
 * #getNotifications); no local tables are involved.
 */
@Service
public class NotificationService {

    private final InternalServiceClient internalServiceClient;
    private final InternalMultipartClient multipartClient;
    private final AdminActivityRepository adminActivityRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String notificationServiceUrl;

    public NotificationService(InternalServiceClient internalServiceClient,
                               InternalMultipartClient multipartClient,
                               AdminActivityRepository adminActivityRepository,
                               @Value("${ridehail.services.notification-url}") String notificationServiceUrl) {
        this.internalServiceClient = internalServiceClient;
        this.multipartClient = multipartClient;
        this.adminActivityRepository = adminActivityRepository;
        this.notificationServiceUrl = notificationServiceUrl;
    }

    /** push_notification: forwards a multipart body to notification-service /send-push-notification. */
    public void pushNotification(com.ridehail.adminservice.dto.SendPushNotificationRequest req, MultipartFile image) {
        List<String> phoneNumbers = req.getPhoneNumbers() == null ? List.of() : req.getPhoneNumbers();

        MultiValueMap<String, Object> form = InternalMultipartClient.newForm();
        form.add("title", req.getTitle());
        form.add("message", req.getDescription());
        form.add("whom", req.getSendTo());
        try {
            form.add("mobile", objectMapper.writeValueAsString(phoneNumbers));
        } catch (Exception e) {
            form.add("mobile", "[]");
        }
        if (image != null && !image.isEmpty()) {
            form.add("imageFile", InternalMultipartClient.filePart(image));
        }

        JsonNode response = multipartClient.post(notificationServiceUrl + "/send-push-notification", form);
        boolean ok = response != null && response.path("status").asBoolean(false);
        if (!ok) {
            int code = response != null && response.hasNonNull("code")
                    ? response.path("code").asInt(StatusCode.BAD_REQUEST)
                    : StatusCode.BAD_REQUEST;
            String message = response != null && response.hasNonNull("message")
                    ? response.path("message").asText()
                    : "Failed to send notification";
            throw new ApiException(code, message);
        }

        logActivity("send_coupon", Map.of(
                "title", req.getTitle(),
                "message", req.getDescription(),
                "sendTo", req.getSendTo(),
                "mobile", phoneNumbers));
    }

    /** get_notification: GET notification-service /get-all-notifications with the same query params. */
    public JsonNode getNotifications(String search, String limit, String page) {
        String s = search == null ? "" : search;
        String l = limit == null ? "10" : limit;
        String p = page == null ? "1" : page;
        String url = notificationServiceUrl + "/get-all-notifications?search="
                + java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8)
                + "&limit=" + l + "&page=" + p;
        return internalServiceClient.get(url);
    }

    private void logActivity(String action, Map<String, Object> meta) {
        try {
            AuthContext ctx = AuthContext.current();
            if (ctx == null || ctx.authUserType() != AuthUserType.ADMIN || ctx.principalId() == null) {
                return;
            }
            UUID adminId;
            try {
                adminId = UUID.fromString(ctx.principalId());
            } catch (IllegalArgumentException e) {
                return;
            }
            AdminActivity activity = new AdminActivity();
            activity.setAdminId(adminId);
            activity.setAction(action);
            activity.setMeta(objectMapper.valueToTree(meta == null ? Map.of() : meta));
            adminActivityRepository.save(activity);
        } catch (Exception ignored) {
            // best-effort, mirrors the original
        }
    }
}
