package com.ridehail.adminservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ridehail.adminservice.dto.UpdateSettingsRequest;
import com.ridehail.adminservice.service.SettingsService;
import com.ridehail.common.response.ApiResponseService;

import jakarta.validation.Valid;

/**
 * Direct port of the //settings routes in Admin-service/routes/admin.routes.js (mounted at
 * /admin-apis): updateSettings + getSettings.
 */
@RestController
@RequestMapping("/admin-apis")
public class SettingsController {

    private final SettingsService settingsService;
    private final ApiResponseService apiResponseService;

    public SettingsController(SettingsService settingsService, ApiResponseService apiResponseService) {
        this.settingsService = settingsService;
        this.apiResponseService = apiResponseService;
    }

    @PostMapping("/update-settings")
    public ResponseEntity<?> updateSettings(@Valid @RequestBody UpdateSettingsRequest request) {
        return apiResponseService.ok("Settings details updated successfully", settingsService.updateSettings(request));
    }

    @GetMapping("/get-settings")
    public ResponseEntity<?> getSettings() {
        return apiResponseService.ok("Settings details", settingsService.getSettings());
    }
}
