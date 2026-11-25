package com.ridehail.notificationservice.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mirrors modal/emailTemplate.shema.js (Mongoose "emailTemplate" model, collection
 * "emailTemplate"). {@code key} is not declared unique:true in the original schema but every
 * write path (add_mail) rejects duplicates by checking for an existing row first, so it is
 * enforced as unique here too.
 */
@Entity
@Table(name = "email_templates")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailTemplate {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "legacy_mongo_id", length = 24, unique = true)
    private String legacyMongoId;

    @Column(name = "subject", nullable = false)
    @Builder.Default
    private String subject = "";

    @Column(name = "content", nullable = false)
    @Builder.Default
    private String content = "";

    @Column(name = "key", nullable = false, unique = true, length = 50)
    @Builder.Default
    private String key = "";

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "active";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
