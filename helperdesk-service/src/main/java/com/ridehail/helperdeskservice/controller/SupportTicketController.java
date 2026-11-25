package com.ridehail.helperdeskservice.controller;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ridehail.common.response.ApiResponseService;
import com.ridehail.helperdeskservice.dto.AddMessageRequest;
import com.ridehail.helperdeskservice.dto.CreateSupportTicketRequest;
import com.ridehail.helperdeskservice.dto.SupportTicketResponse;
import com.ridehail.helperdeskservice.service.SupportTicketService;

import jakarta.validation.Valid;

/** Port of routes/helperdesk.routes.js's support-ticket routes (mounted under "/helperdesk-apis"). */
@RestController
@RequestMapping("/helperdesk-apis")
public class SupportTicketController {

    private final SupportTicketService supportTicketService;
    private final ApiResponseService apiResponseService;

    public SupportTicketController(SupportTicketService supportTicketService, ApiResponseService apiResponseService) {
        this.supportTicketService = supportTicketService;
        this.apiResponseService = apiResponseService;
    }

    // ---- create-support-ticket : JSON body or multipart (with optional supportImage) ----

    @PostMapping(value = "/create-support-ticket", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createJson(@Valid @RequestBody CreateSupportTicketRequest request) {
        SupportTicketResponse data = supportTicketService.createSupportTicket(request, null);
        return apiResponseService.ok("Support ticket created successfully.", data);
    }

    @PostMapping(value = "/create-support-ticket", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE,
            MediaType.APPLICATION_FORM_URLENCODED_VALUE })
    public ResponseEntity<?> createMultipart(
            @Valid @ModelAttribute CreateSupportTicketRequest request,
            @RequestParam(value = "supportImage", required = false) MultipartFile supportImage) {
        SupportTicketResponse data = supportTicketService.createSupportTicket(request, supportImage);
        return apiResponseService.ok("Support ticket created successfully.", data);
    }

    // ---- add-message : JSON body or multipart ----

    @PutMapping(value = "/add-message", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> addMessageJson(@Valid @RequestBody AddMessageRequest request) {
        SupportTicketResponse data = supportTicketService.addMessageOnTicket(request, null);
        return apiResponseService.ok("Support ticket updated successfully.", data);
    }

    @PutMapping(value = "/add-message", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE,
            MediaType.APPLICATION_FORM_URLENCODED_VALUE })
    public ResponseEntity<?> addMessageMultipart(
            @Valid @ModelAttribute AddMessageRequest request,
            @RequestParam(value = "supportImage", required = false) MultipartFile supportImage) {
        SupportTicketResponse data = supportTicketService.addMessageOnTicket(request, supportImage);
        return apiResponseService.ok("Support ticket updated successfully.", data);
    }

    @GetMapping("/get-support-tickets-by-user")
    public ResponseEntity<?> getByUser(
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "skip", required = false) Integer skip) {
        Map<String, Object> data = supportTicketService.getSupportTicketsByUser(limit, skip);
        return apiResponseService.ok("Support tickets fetched successfully.", data);
    }

    @GetMapping("/get-support-tickets-by-admin")
    public ResponseEntity<?> getByAdmin(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "complainType", required = false) String complainType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "search", required = false) String search) {
        Map<String, Object> data = supportTicketService.getSupportTicketsByAdmin(page, limit, complainType, status, search);
        return apiResponseService.ok("Support ticket fetched successfully.", data);
    }

    @DeleteMapping("/close-support-ticket")
    public ResponseEntity<?> closeTicket(
            @RequestParam("ticketId") String ticketId,
            @RequestParam("closingMessage") String closingMessage) {
        SupportTicketResponse data = supportTicketService.closeSupportTicketByAdmin(ticketId, closingMessage);
        return apiResponseService.ok("Support ticket closed successfully.", data);
    }

    @PatchMapping("/reopen-support-ticket")
    public ResponseEntity<?> reopenTicket(@RequestParam("ticketId") String ticketId) {
        SupportTicketResponse data = supportTicketService.reopenSupportTicketByAdmin(ticketId);
        return apiResponseService.ok("Support ticket reopened successfully.", data);
    }
}
