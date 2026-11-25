package com.ridehail.helperdeskservice.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /create-support-ticket. Mirrors createSupportSchema (subject required, message optional). */
public class CreateSupportTicketRequest {

    @NotBlank
    private String subject;

    private String message;

    private String complainType;

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getComplainType() {
        return complainType;
    }

    public void setComplainType(String complainType) {
        this.complainType = complainType;
    }
}
