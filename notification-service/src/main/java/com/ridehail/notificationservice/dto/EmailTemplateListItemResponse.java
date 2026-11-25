package com.ridehail.notificationservice.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response shape for GET /get-mails (get_emails) - mirrors {@code EmailTemplate.find({})} in
 * service/mailTemplate.service.js's getAllTemplate, which returns the full document (no
 * projection), unlike getMailTemplate's trimmed {@link EmailTemplateResponse}.
 */
public class EmailTemplateListItemResponse {

    @JsonProperty("_id")
    private UUID id;

    private String subject;

    private String content;

    private String key;

    private String status;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    public EmailTemplateListItemResponse() {
    }

    public EmailTemplateListItemResponse(UUID id, String subject, String content, String key, String status,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.subject = subject;
        this.content = content;
        this.key = key;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
