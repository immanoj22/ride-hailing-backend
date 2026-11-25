package com.ridehail.notificationservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ridehail.notificationservice.entity.EmailTemplate;

/**
 * Backs service/mailTemplate.service.js's addMailTemplate / getMailTemplate / updateMailTemplate /
 * getAllTemplate helpers. getAllTemplate() (EmailTemplate.find({})) is covered by the inherited
 * findAll().
 */
public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, UUID> {

    /** getMailTemplate({key}) / add_mail's & edit_mail's existence checks. */
    Optional<EmailTemplate> findByKey(String key);
}
