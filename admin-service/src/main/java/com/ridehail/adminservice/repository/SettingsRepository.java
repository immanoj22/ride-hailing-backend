package com.ridehail.adminservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ridehail.adminservice.entity.Settings;

public interface SettingsRepository extends JpaRepository<Settings, UUID> {

    /**
     * update_settings/getSettings both operate on {@code settings.findOne({})} - i.e. whichever
     * single settings row exists (the collection is treated as a singleton).
     */
    Optional<Settings> findFirstByOrderByCreatedAtAsc();
}
