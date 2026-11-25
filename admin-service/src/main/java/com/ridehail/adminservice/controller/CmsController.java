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

import com.ridehail.adminservice.dto.AddCmsRequest;
import com.ridehail.adminservice.dto.EditCmsRequest;
import com.ridehail.adminservice.service.CmsService;
import com.ridehail.common.response.ApiResponseService;

import jakarta.validation.Valid;

/**
 * Direct port of the CMS routes in Admin-service/routes/admin.routes.js
 * (mounted at {@code /admin-apis} by server.js).
 */
@RestController
@RequestMapping("/admin-apis")
public class CmsController {

    private final CmsService cmsService;
    private final ApiResponseService apiResponseService;

    public CmsController(CmsService cmsService, ApiResponseService apiResponseService) {
        this.cmsService = cmsService;
        this.apiResponseService = apiResponseService;
    }

    @PostMapping("/add-cms")
    public ResponseEntity<?> addCms(@Valid @RequestBody AddCmsRequest req) {
        return apiResponseService.ok("Added successfully", cmsService.addCms(req));
    }

    @GetMapping("/get-cms/{id}")
    public ResponseEntity<?> getCms(@PathVariable("id") String id) {
        return apiResponseService.ok("CMS documents are available", cmsService.getCms(id));
    }

    @GetMapping("/get-all-cms")
    public ResponseEntity<?> getAllCms() {
        return apiResponseService.ok("CMS documents are available", cmsService.getAllCms());
    }

    @PutMapping("/edit-cms")
    public ResponseEntity<?> editCms(@Valid @RequestBody EditCmsRequest req) {
        String message = cmsService.editCms(req);
        return apiResponseService.ok(message, java.util.Map.of());
    }

    @GetMapping("/get-cms-by-page")
    public ResponseEntity<?> getCmsByPage(@RequestParam(value = "page", required = false) String page) {
        return apiResponseService.ok("CMS documents are available", cmsService.getCmsByPage(page));
    }
}
