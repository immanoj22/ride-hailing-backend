package com.ridehail.notificationservice.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response shape for GET /get-mail/{key} (get_email) - mirrors
 * {@code EmailTemplate.findOne(options).select("_id subject content key")} in
 * service/mailTemplate.service.js's getMailTemplate: only these four fields, no status/timestamps.
 * The Mongo document's {@code _id} key is preserved on the wire via {@code @JsonProperty}.
 */
public class EmailTemplateResponse {

    @JsonProperty("_id")
    private UUID id;

    private String subject;

    private String content;

    private String key;

    public EmailTemplateResponse() {
    }

    public EmailTemplateResponse(UUID id, String subject, String content, String key) {
        this.id = id;
        this.subject = subject;
        this.content = content;
        this.key = key;
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
}
