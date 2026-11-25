package com.ridehail.helperdeskservice.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.ridehail.helperdeskservice.entity.SosAlert;

/** Wire shape of a Mongoose "SOS" document, optionally enriched with the linked user/driver. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SosResponse(
        @JsonProperty("_id") UUID id,
        UUID rideId,
        UUID userId,
        String userType,
        Location location,
        OffsetDateTime triggeredAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        JsonNode user,
        JsonNode driver) {

    public record Location(String type, List<Double> coordinates) {
    }

    public static SosResponse from(SosAlert s, JsonNode user, JsonNode driver) {
        Location loc = (s.getLocationLat() == null && s.getLocationLng() == null) ? null
                : new Location("Point", List.of(
                        s.getLocationLng() == null ? 0.0 : s.getLocationLng(),
                        s.getLocationLat() == null ? 0.0 : s.getLocationLat()));
        return new SosResponse(s.getId(), s.getRideId(), s.getUserId(), s.getUserType(), loc,
                s.getTriggeredAt(), s.getCreatedAt(), s.getUpdatedAt(), user, driver);
    }
}
