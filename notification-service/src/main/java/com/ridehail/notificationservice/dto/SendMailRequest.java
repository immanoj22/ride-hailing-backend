package com.ridehail.notificationservice.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /send-mail. Mirrors validations/mailTemplateValidation.js's
 * sendEmailSchema: {@code key} is a required string (2-50 chars), {@code mailContent} is a
 * required, otherwise free-form object (Joi.object().unknown(true)) - it always carries the
 * recipient's {@code email} plus whatever placeholder values the template needs, so it's kept as
 * a generic map rather than a fixed-shape DTO.
 */
public class SendMailRequest {

    @NotBlank
    @Size(min = 2, max = 50)
    private String key;

    @NotNull
    private Map<String, Object> mailContent;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Map<String, Object> getMailContent() {
        return mailContent;
    }

    public void setMailContent(Map<String, Object> mailContent) {
        this.mailContent = mailContent;
    }
}
