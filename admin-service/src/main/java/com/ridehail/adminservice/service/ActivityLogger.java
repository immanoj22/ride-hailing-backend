package com.ridehail.adminservice.service;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ridehail.adminservice.entity.AdminActivity;
import com.ridehail.adminservice.repository.AdminActivityRepository;

/**
 * Port of {@code logActivity(data)} / {@code saveActivity} (helpers/admin.commonFunction.js +
 * services/admin.services.js). Fire-and-forget: a logging failure is caught and never propagated,
 * exactly like the original {@code try/catch} that only {@code console.error}s.
 */
@Component
public class ActivityLogger {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ActivityLogger.class);

    private final AdminActivityRepository adminActivityRepository;

    public ActivityLogger(AdminActivityRepository adminActivityRepository) {
        this.adminActivityRepository = adminActivityRepository;
    }

    public void log(UUID adminId, String action, String ip) {
        try {
            AdminActivity activity = new AdminActivity();
            activity.setAdminId(adminId);
            activity.setAction(action);
            activity.setIp(ip == null ? "" : ip);
            activity.setDevice("");
            activity.setMeta(JsonNodeFactory.instance.objectNode());
            adminActivityRepository.save(activity);
        } catch (Exception e) {
            log.error("Activity Log Error: {}", e.getMessage());
        }
    }
}
