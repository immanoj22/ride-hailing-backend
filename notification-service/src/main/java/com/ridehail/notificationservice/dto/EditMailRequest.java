package com.ridehail.notificationservice.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body for PUT /edit-mail/{key}. Mirrors validations/mailTemplateValidation.js's
 * editEmailSchema - both fields optional; the {@code key} itself is a path param (validated
 * separately on the controller method, see editEmailSchema's {@code params.key}).
 */
public class EditMailRequest {

    @Size(min = 3, max = 100)
    private String subject;

    @Size(min = 5)
    private String content;

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
}
