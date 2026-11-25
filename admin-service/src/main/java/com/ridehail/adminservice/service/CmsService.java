package com.ridehail.adminservice.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ridehail.adminservice.dto.AddCmsRequest;
import com.ridehail.adminservice.dto.EditCmsRequest;
import com.ridehail.adminservice.entity.Admin;
import com.ridehail.adminservice.entity.Cms;
import com.ridehail.adminservice.repository.AdminRepository;
import com.ridehail.adminservice.repository.CmsRepository;
import com.ridehail.adminservice.util.DocView;
import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.common.security.AuthContext;

/**
 * Port of the CMS controller functions in Admin-service/controller/admin.controller.js:
 * add_cms, get_cms, getAllcms, edit_cms, getCmsByPage (backed by services/cms.services.js).
 * The Redis {@code route:*} cache-busting in the original is a no-op here (no HTTP cache layer).
 */
@Service
public class CmsService {

    private final CmsRepository cmsRepository;
    private final AdminRepository adminRepository;
    private final ActivityLogger activityLogger;

    public CmsService(CmsRepository cmsRepository, AdminRepository adminRepository, ActivityLogger activityLogger) {
        this.cmsRepository = cmsRepository;
        this.adminRepository = adminRepository;
        this.activityLogger = activityLogger;
    }

    @Transactional
    public Map<String, Object> addCms(AddCmsRequest req) {
        Admin admin = currentAdminOrThrow();
        cmsRepository.findByNameAndDeletedFalse(req.getName()).ifPresent(c -> {
            throw new ApiException(StatusCode.BAD_REQUEST, "Name must be unique");
        });

        Cms cms = new Cms();
        cms.setName(req.getName());
        cms.setSlug(req.getSlug());
        cms.setPage(req.getPage());
        cms.setType(req.getType());
        cms.setLink(req.getLink() == null ? "" : req.getLink());
        cms.setQuestion(req.getQuestion() == null ? "" : req.getQuestion());
        cms.setAnswer(req.getAnswer() == null ? "" : req.getAnswer());
        cms.setImage(""); // original multipart imgFile -> S3 upload is not ported in this service
        Cms saved = cmsRepository.save(cms);

        activityLogger.log(admin.getId(), "add_cms", null);
        return DocView.cms(saved);
    }

    @Transactional
    public String editCms(EditCmsRequest req) {
        Admin admin = currentAdminOrThrow();
        UUID id = parseUuid(req.getId());

        if ("delete".equals(req.getAction())) {
            Cms cms = cmsRepository.findById(id)
                    .orElseThrow(() -> new ApiException(StatusCode.BAD_REQUEST, "CMS not deleted"));
            cms.setDeleted(true);
            cmsRepository.save(cms);
            activityLogger.log(admin.getId(), "delete_cms", null);
            return "Deleted successfully";
        }
        if ("edit".equals(req.getAction())) {
            cmsRepository.findByNameAndDeletedFalseAndIdNot(req.getName(), id).ifPresent(c -> {
                throw new ApiException(StatusCode.BAD_REQUEST, "Name must be unique");
            });
            Cms cms = cmsRepository.findById(id)
                    .orElseThrow(() -> new ApiException(StatusCode.BAD_REQUEST, "CMS not updated"));
            cms.setName(req.getName());
            cms.setSlug(req.getSlug());
            cms.setPage(req.getPage());
            cms.setType(req.getType());
            cms.setLink(req.getLink() == null ? "" : req.getLink());
            cms.setQuestion(req.getQuestion() == null ? "" : req.getQuestion());
            cms.setAnswer(req.getAnswer() == null ? "" : req.getAnswer());
            cmsRepository.save(cms);
            activityLogger.log(admin.getId(), "edit_cms", null);
            return "Updated successfully";
        }
        throw new ApiException(StatusCode.BAD_REQUEST, "Invalid action");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCms(String id) {
        currentAdminOrThrow();
        Cms cms = cmsRepository.findById(parseUuid(id))
                .orElseThrow(() -> new ApiException(StatusCode.BAD_REQUEST, "Invalid cms Id"));
        return DocView.cms(cms);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAllCms() {
        currentAdminOrThrow();
        Page<Cms> page = cmsRepository.findByDeletedFalse(Pageable.unpaged());
        return documentAndTotal(page.getContent(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCmsByPage(String pageName) {
        List<Cms> docs = cmsRepository.findByPage(pageName);
        return documentAndTotal(docs, docs.size());
    }

    /* -------------------------------------------------------------------- helpers */

    private Map<String, Object> documentAndTotal(List<Cms> docs, long total) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("document", docs.stream().map(DocView::cms).collect(Collectors.toList()));
        out.put("total", total);
        return out;
    }

    private Admin currentAdminOrThrow() {
        AuthContext ctx = AuthContext.current();
        if (ctx == null || ctx.principalId() == null) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Invalid adminId");
        }
        try {
            return adminRepository.findById(UUID.fromString(ctx.principalId()))
                    .orElseThrow(() -> new ApiException(StatusCode.BAD_REQUEST, "Invalid adminId"));
        } catch (IllegalArgumentException e) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Invalid adminId");
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Invalid cms Id");
        }
    }
}
