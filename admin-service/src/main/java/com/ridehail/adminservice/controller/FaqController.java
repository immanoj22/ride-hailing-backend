package com.ridehail.adminservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ridehail.adminservice.dto.AddFaqRequest;
import com.ridehail.adminservice.dto.EditFaqRequest;
import com.ridehail.adminservice.service.FaqService;
import com.ridehail.common.response.ApiResponseService;

import jakarta.validation.Valid;

/**
 * Direct port of the FAQ routes in Admin-service/routes/admin.routes.js
 * (mounted at {@code /admin-apis} by server.js).
 */
@RestController
@RequestMapping("/admin-apis")
public class FaqController {

    private final FaqService faqService;
    private final ApiResponseService apiResponseService;

    public FaqController(FaqService faqService, ApiResponseService apiResponseService) {
        this.faqService = faqService;
        this.apiResponseService = apiResponseService;
    }

    @PostMapping("/add-faq")
    public ResponseEntity<?> addFaq(@Valid @RequestBody AddFaqRequest req) {
        return apiResponseService.ok("FAQ Added successfully", faqService.addFaq(req));
    }

    @GetMapping("/get-faq/{id}")
    public ResponseEntity<?> getFaq(@PathVariable("id") String id) {
        return apiResponseService.ok("FAQ documents are available", faqService.getFaq(id));
    }

    @GetMapping("/get-all-faq")
    public ResponseEntity<?> getAllFaq() {
        return apiResponseService.ok("FAQ documents are available", faqService.getAllFaq());
    }

    @GetMapping("/get-faq-type")
    public ResponseEntity<?> getFaqByType(@RequestParam(value = "type", required = false) String type) {
        return apiResponseService.ok("FAQ documents are available", faqService.getFaqByType(type));
    }

    @PutMapping("/edit-faq")
    public ResponseEntity<?> editFaq(@Valid @RequestBody EditFaqRequest req) {
        String message = faqService.editFaq(req);
        return apiResponseService.ok(message, java.util.Map.of());
    }
}
