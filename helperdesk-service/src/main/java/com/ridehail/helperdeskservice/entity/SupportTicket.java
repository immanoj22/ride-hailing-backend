package com.ridehail.helperdeskservice.entity;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Equivalent of modals/support.schema.js's top-level "support" document. The embedded "messages"
 * subdocuments are modeled as the child entity {@link SupportMessage} (see support_messages
 * table) rather than a JSONB blob, per the DATABASE RULES (real columns, not whole-document dumps).
 */
@Entity
@Table(name = "support_tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Nullable/unique/indexed bridge column for a future Mongo _id backfill - never required. */
    @Column(name = "legacy_mongo_id", length = 24, unique = true)
    private String legacyMongoId;

    /** Owning user or driver id (owned by user-service/driver-service - plain uuid, no FK). */
    @Column(name = "user_id")
    private UUID userId;

    @Builder.Default
    @Column(name = "subject", nullable = false)
    private String subject = "";

    /** "user" or "driver" - who raised the ticket (support.schema.js "from", required). */
    @Column(name = "from_type", nullable = false, length = 10)
    private String fromType;

    /** "refund" | "ride issue" | "payment issue" | "other" - optional, no default in Mongoose. */
    @Column(name = "complain_type", length = 20)
    private String complainType;

    /** true = open, false = closed (support.schema.js "status", default true). */
    @Builder.Default
    @Column(name = "status", nullable = false)
    private Boolean status = true;

    @Builder.Default
    @Column(name = "closing_message", nullable = false)
    private String closingMessage = "";

    @Builder.Default
    @OneToMany(mappedBy = "supportTicket", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<SupportMessage> messages = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
