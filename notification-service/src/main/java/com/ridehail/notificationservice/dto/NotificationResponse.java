package com.ridehail.notificationservice.dto;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ridehail.notificationservice.entity.Notification;

/** Wire shape of a Mongoose "Notification" document - preserves the capitalized "Whom" key. */
public record NotificationResponse(
        @JsonProperty("_id") UUID id,
        String image,
        String title,
        String message,
        String body,
        @JsonProperty("Whom") String whom,
        String mobile,
        String coupons,
        List<UUID> userIds,
        List<UUID> driverIds,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getImage(), n.getTitle(), n.getMessage(), n.getBody(), n.getWhom(),
                n.getMobile(), n.getCoupons(),
                n.getUserIds() == null ? List.of() : Arrays.asList(n.getUserIds()),
                n.getDriverIds() == null ? List.of() : Arrays.asList(n.getDriverIds()),
                n.getCreatedAt(), n.getUpdatedAt());
    }
}
