package com.ridehail.helperdeskservice.dto;

import jakarta.validation.constraints.NotBlank;

/** PUT /add-message. addMessageSchema exists in helperdesk.validation.js but the route does NOT
 *  apply it in the original; still, supportTicketId + sender are required by the handler logic. */
public class AddMessageRequest {

    @NotBlank
    private String supportTicketId;

    private String message;

    @NotBlank
    private String sender;

    public String getSupportTicketId() {
        return supportTicketId;
    }

    public void setSupportTicketId(String supportTicketId) {
        this.supportTicketId = supportTicketId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }
}
