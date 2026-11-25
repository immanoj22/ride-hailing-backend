package com.ridehail.notificationservice.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.notificationservice.dto.AddMailRequest;
import com.ridehail.notificationservice.dto.EditMailRequest;
import com.ridehail.notificationservice.dto.EmailTemplateListItemResponse;
import com.ridehail.notificationservice.dto.EmailTemplateResponse;
import com.ridehail.notificationservice.dto.SendMailRequest;
import com.ridehail.notificationservice.entity.EmailTemplate;
import com.ridehail.notificationservice.repository.EmailTemplateRepository;

import jakarta.mail.internet.MimeMessage;

/**
 * Direct port of controllers/notification.controller.js's add_mail / edit_mail / get_email /
 * get_emails / sendMailToUser, backed by service/mailTemplate.service.js's addMailTemplate /
 * getMailTemplate / updateMailTemplate / getAllTemplate / send_email / templateReplacers.
 */
@Service
@Transactional(readOnly = true)
public class EmailTemplateService {

    private static final Logger log = LoggerFactory.getLogger(EmailTemplateService.class);

    /** service/mailTemplate.service.js's templateReplacers placeholder syntax: [[[key]]]. */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\[\\[\\[(.*?)]]]");

    /** helper/constants.js's KEYS.createMailtemplateKey. */
    private static final String CACHE_KEY_PREFIX = "mailTemplate/";

    /** sendMailToUser's redisCache.set(tempKey, getMail, 300). */
    private static final long TEMPLATE_CACHE_TTL_SECONDS = 300;

    private final EmailTemplateRepository emailTemplateRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ITemplateEngine templateEngine;
    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailTemplateService(
            EmailTemplateRepository emailTemplateRepository,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            ITemplateEngine templateEngine,
            JavaMailSender mailSender,
            @Value("${ridehail.mail.from-address:}") String fromAddress) {
        this.emailTemplateRepository = emailTemplateRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.templateEngine = templateEngine;
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    /**
     * POST /add-mail. add_mail rejects a duplicate {@code key} (checked via getMailTemplate)
     * before creating - the DB's unique index on {@code key} backs this up but the pre-check
     * keeps the "Key already exist" message exact.
     */
    @Transactional
    public void addMail(AddMailRequest request) {
        if (emailTemplateRepository.findByKey(request.getKey()).isPresent()) {
            throw new ApiException(StatusCode.CONFLICT, "Key already exist");
        }
        EmailTemplate template = EmailTemplate.builder()
                .key(request.getKey())
                .subject(request.getSubject())
                .content(request.getContent())
                .build();
        emailTemplateRepository.save(template);
    }

    /**
     * PUT /edit-mail/{key}. editEmailSchema allows subject/content to be omitted independently,
     * and updateMailTemplate's findOneAndUpdate only ever touches the fields actually present on
     * {@code data} - reproduced here by only overwriting fields that were supplied.
     */
    @Transactional
    public void editMail(String key, EditMailRequest request) {
        EmailTemplate template = emailTemplateRepository.findByKey(key)
                .orElseThrow(() -> new ApiException(StatusCode.NOT_FOUND, "Email is not added"));
        if (request.getSubject() != null) {
            template.setSubject(request.getSubject());
        }
        if (request.getContent() != null) {
            template.setContent(request.getContent());
        }
        emailTemplateRepository.save(template);
    }

    /** GET /get-mail/{key}. get_email / getMailTemplate's trimmed "_id subject content key" projection. */
    public EmailTemplateResponse getEmail(String key) {
        EmailTemplate template = emailTemplateRepository.findByKey(key)
                .orElseThrow(() -> new ApiException(StatusCode.NOT_FOUND, "Not found"));
        return new EmailTemplateResponse(template.getId(), template.getSubject(), template.getContent(), template.getKey());
    }

    /** GET /get-mails. get_emails / getAllTemplate's full EmailTemplate.find({}). */
    public List<EmailTemplateListItemResponse> getEmails() {
        List<EmailTemplateListItemResponse> result = new ArrayList<>();
        for (EmailTemplate template : emailTemplateRepository.findAll()) {
            result.add(new EmailTemplateListItemResponse(template.getId(), template.getSubject(), template.getContent(),
                    template.getKey(), template.getStatus(), template.getCreatedAt(), template.getUpdatedAt()));
        }
        return result;
    }

    /**
     * POST /send-mail. sendMailToUser: fetch the template (redis-cached for 300s under
     * "mailTemplate/{key}", exactly like the original redisCache.get/set), substitute
     * [[[placeholder]]] tokens from mailContent, render views/mailTemplate.pug (here:
     * templates/mailTemplate.html) and send it via send_email.
     */
    public void sendMailToUser(SendMailRequest request) {
        EmailTemplate template = getCachedTemplate(request.getKey());
        if (template == null) {
            throw new ApiException(StatusCode.NOT_FOUND, "Key Not found");
        }

        RenderedText rendered = applyPlaceholders(template.getSubject(), template.getContent(), request.getMailContent());

        Context context = new Context();
        context.setVariable("subject", rendered.subject());
        context.setVariable("content", rendered.content());
        // views/mailTemplate.pug's #{name} is never supplied by any caller of pug.renderFile -
        // preserved here as a blank value rather than inventing a name source.
        context.setVariable("name", "");
        String html = templateEngine.process("mailTemplate", context);

        Object emailValue = request.getMailContent().get("email");
        String email = emailValue == null ? null : String.valueOf(emailValue);

        boolean sent = sendEmail(email, rendered.subject(), html);
        if (!sent) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Email not sent");
        }
    }

    /** getMailTemplate({key}), cached the same way sendMailToUser caches it (300s TTL). */
    private EmailTemplate getCachedTemplate(String key) {
        String cacheKey = CACHE_KEY_PREFIX + key;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.readValue(cached, EmailTemplate.class);
            }
        } catch (Exception e) {
            log.warn("Failed to read mail template cache for key {}", key, e);
        }

        EmailTemplate template = emailTemplateRepository.findByKey(key).orElse(null);
        if (template != null) {
            try {
                redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(template), TEMPLATE_CACHE_TTL_SECONDS,
                        java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Failed to cache mail template for key {}", key, e);
            }
        }
        return template;
    }

    /**
     * templateReplacers(subject, str, replacers): substitutes every [[[key]]] placeholder found
     * in either string from {@code replacers}, collecting every placeholder with no matching
     * entry (duplicates included, exactly like the original) and failing with the same message.
     */
    private RenderedText applyPlaceholders(String subject, String content, Map<String, Object> replacers) {
        List<String> missingKeys = new ArrayList<>();
        String newSubject = processTemplate(subject, replacers, missingKeys);
        String newContent = processTemplate(content, replacers, missingKeys);
        if (!missingKeys.isEmpty()) {
            throw new ApiException(StatusCode.INTERNAL_SERVER_ERROR,
                    "Missing value for placeholder: " + String.join(", ", missingKeys));
        }
        return new RenderedText(newSubject, newContent);
    }

    private String processTemplate(String input, Map<String, Object> replacers, List<String> missingKeys) {
        if (input == null) {
            return null;
        }
        String output = input;
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(input);
        while (matcher.find()) {
            String key = matcher.group(1);
            if (replacers == null || !replacers.containsKey(key)) {
                missingKeys.add(key);
                continue;
            }
            String literal = "[[[" + key + "]]]";
            output = output.replace(literal, String.valueOf(replacers.get(key)));
        }
        return output;
    }

    /**
     * send_email: the original hardcodes a live Gmail SMTP account+app-password here, bypassing
     * the already-computed CONFIG.EMAIL_USER/EMAIL_PASS (zeptomail) values entirely - flagged for
     * credential rotation. Ported to source host/port/credentials from application.yml/env
     * (spring.mail.*) instead of hardcoding them in Java; the mismatched "from" address
     * (zeptomail address, Gmail transport) is preserved as-is per "preserve behaviour".
     */
    private boolean sendEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            if (fromAddress != null && !fromAddress.isBlank()) {
                helper.setFrom(fromAddress);
            }
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            // Mirrors send_email's catch-all: any transport/addressing failure (e.g. a missing
            // "email" in mailContent) is swallowed and reported as "false", not propagated.
            log.error("send_email error", e);
            return false;
        }
    }

    private record RenderedText(String subject, String content) {
    }
}
