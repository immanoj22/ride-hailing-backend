package com.ridehail.adminservice.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Bean-Validation port of validations/coupon.validation.js#sendPushNotificationValidation. Bound
 * from the multipart form of POST /admin-apis/send-push-notification (the {@code image} file part
 * is handled separately in the controller). The "phoneNumbers required when sendTo is
 * selectedUser/selectedDriver" conditional is enforced downstream by notification-service exactly
 * as before (the admin controller only forwards).
 */
@Getter
@Setter
public class SendPushNotificationRequest {

    @NotNull
    @Size(min = 3, max = 100)
    private String title;

    @NotNull
    @Size(min = 3, max = 500)
    private String description;

    @NotNull
    @Pattern(regexp = "user|driver|selectedUser|selectedDriver")
    private String sendTo;

    private List<String> phoneNumbers;
}
