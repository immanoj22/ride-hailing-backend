package com.ridehail.socketservice.entity;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Equivalent of modals/socket.schema.js (Mongoose "Chat" model, collection "Chat").
 *
 * <p>The declared schema only has rideId/senderId/from/msg/file/seen, but
 * services/socket.services.js's query helpers (get_Ride_Message, get_chat_message,
 * get_chat_from_user) additionally filter on driverId/userId/ticketId - fields Mongoose silently
 * drops on write because they aren't declared on the schema (so those particular query paths never
 * actually match anything in the legacy app). The columns are kept here anyway so the
 * Implementation-step port of those same query helpers has a real place to read/write them,
 * exactly mirroring the shape the original code assumes.</p>
 */
@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "legacy_mongo_id", length = 24, unique = true)
    private String legacyMongoId;

    /** ride-service owns rides - plain uuid, no FK. */
    @Column(name = "ride_id")
    private UUID rideId;

    /** Generic sender id (user or driver, per {@link #from}) used by the ride-chat flow
     *  (chat:user:send / saveChatMessage). */
    @Column(name = "sender_id")
    private UUID senderId;

    /** driver-service owns drivers - plain uuid, no FK. Used by the (currently dead) ticket/ride
     *  admin-chat query helpers. */
    @Column(name = "driver_id")
    private UUID driverId;

    /** user-service owns users - plain uuid, no FK. Used by the (currently dead) direct
     *  admin&lt;-&gt;user chat query helpers. */
    @Column(name = "user_id")
    private UUID userId;

    /** helperdesk-service owns support tickets - plain uuid, no FK. Used by
     *  getChatUsingTicketId/get_chat_message. */
    @Column(name = "ticket_id")
    private UUID ticketId;

    /** Mongoose enum ["user", "driver"]. */
    @Column(name = "from_type", nullable = false, length = 10)
    private String from;

    @Builder.Default
    @Column(name = "msg", nullable = false, columnDefinition = "text")
    private String msg = "";

    @Builder.Default
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "file", nullable = false, columnDefinition = "text[]")
    private List<String> file = new ArrayList<>();

    @Builder.Default
    @Column(name = "seen", nullable = false)
    private boolean seen = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
