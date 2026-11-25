package com.ridehail.notificationservice.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ridehail.common.response.ApiResponseService;
import com.ridehail.notificationservice.dto.AddMailRequest;
import com.ridehail.notificationservice.dto.EditMailRequest;
import com.ridehail.notificationservice.dto.EmailTemplateListItemResponse;
import com.ridehail.notificationservice.dto.EmailTemplateResponse;
import com.ridehail.notificationservice.dto.SendMailRequest;
import com.ridehail.notificationservice.service.EmailTemplateService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * Direct port of routes/notification.routes.js's mail-template endpoints (mounted under
 * "/notification-apis" by server.js's {@code app.use("/notification-apis", NotificationRoute)}).
 * No business logic here - see {@link EmailTemplateService}.
 */
@RestController
@RequestMapping("/notification-apis")
@Validated
public class EmailTemplateController {

    private final EmailTemplateService emailTemplateService;
    private final ApiResponseService apiResponseService;

    public EmailTemplateController(EmailTemplateService emailTemplateService, ApiResponseService apiResponseService) {
        this.emailTemplateService = emailTemplateService;
        this.apiResponseService = apiResponseService;
    }

    /** POST /add-mail. */
    @PostMapping("/add-mail")
    public ResponseEntity<?> addMail(@Valid @RequestBody AddMailRequest request) {
        emailTemplateService.addMail(request);
        return apiResponseService.ok("Email template added successfully", Map.of());
    }

    /** PUT /edit-mail/:key. */
    @PutMapping("/edit-mail/{key}")
    public ResponseEntity<?> editMail(
            @PathVariable @Size(min = 2, max = 50) String key,
            @Valid @RequestBody EditMailRequest request) {
        emailTemplateService.editMail(key, request);
        return apiResponseService.ok("Email template updated successfully", Map.of());
    }

    /** GET /get-mail/:key. */
    @GetMapping("/get-mail/{key}")
    public ResponseEntity<?> getEmail(@PathVariable @Size(min = 2, max = 50) String key) {
        EmailTemplateResponse response = emailTemplateService.getEmail(key);
        return apiResponseService.ok("Email Template is available", response);
    }

    /** GET /get-mails. */
    @GetMapping("/get-mails")
    public ResponseEntity<?> getEmails() {
        List<EmailTemplateListItemResponse> response = emailTemplateService.getEmails();
        return apiResponseService.ok("Email Templates are available", response);
    }

    /** POST /send-mail. */
    @PostMapping("/send-mail")
    public ResponseEntity<?> sendMailToUser(@Valid @RequestBody SendMailRequest request) {
        emailTemplateService.sendMailToUser(request);
        return apiResponseService.ok("Email sent successfully", Map.of());
    }
}
