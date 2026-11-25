package com.ridehail.helperdeskservice.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.ridehail.helperdeskservice.entity.SupportMessage;
import com.ridehail.helperdeskservice.entity.SupportTicket;

/** Wire shape of a Mongoose "support" document (+ embedded messages), optionally enriched. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SupportTicketResponse(
        @JsonProperty("_id") UUID id,
        UUID userId,
        String subject,
        @JsonProperty("from") String fromType,
        String complainType,
        Boolean status,
        String closingMessage,
        List<Message> messages,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        JsonNode user,
        JsonNode driver) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Message(
            @JsonProperty("_id") UUID id,
            String message,
            String image,
            String sender,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {

        static Message from(SupportMessage m) {
            return new Message(m.getId(), m.getMessage(), m.getImage(), m.getSender(), m.getCreatedAt(), m.getUpdatedAt());
        }
    }

    public static SupportTicketResponse from(SupportTicket t) {
        return from(t, null, null);
    }

    public static SupportTicketResponse from(SupportTicket t, JsonNode user, JsonNode driver) {
        List<Message> msgs = t.getMessages() == null ? List.of()
                : t.getMessages().stream().map(Message::from).toList();
        return new SupportTicketResponse(t.getId(), t.getUserId(), t.getSubject(), t.getFromType(),
                t.getComplainType(), t.getStatus(), t.getClosingMessage(), msgs,
                t.getCreatedAt(), t.getUpdatedAt(), user, driver);
    }
}
