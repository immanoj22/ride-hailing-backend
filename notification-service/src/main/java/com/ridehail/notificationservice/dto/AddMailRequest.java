package com.ridehail.notificationservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /add-mail. Mirrors validations/mailTemplateValidation.js's
 * addEmailSchema (key/subject/content all required, joi string min/max rules translated to
 * bean-validation annotations).
 */
public class AddMailRequest {

    @NotBlank
    @Size(min = 2, max = 50)
    private String key;

    @NotBlank
    @Size(min = 3, max = 100)
    private String subject;

    @NotBlank
    @Size(min = 5)
    private String content;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
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
}
