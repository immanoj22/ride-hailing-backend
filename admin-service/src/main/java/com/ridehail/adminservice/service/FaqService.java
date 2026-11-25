package com.ridehail.adminservice.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ridehail.adminservice.dto.AddFaqRequest;
import com.ridehail.adminservice.dto.EditFaqRequest;
import com.ridehail.adminservice.entity.Admin;
import com.ridehail.adminservice.entity.Faq;
import com.ridehail.adminservice.repository.AdminRepository;
import com.ridehail.adminservice.repository.FaqRepository;
import com.ridehail.adminservice.util.DocView;
import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.common.security.AuthContext;

/**
 * Port of the FAQ controller functions in Admin-service/controller/admin.controller.js:
 * add_faq, get_faq, getAllFaq, getFaqByType, edit_faq (backed by services/faq.services.js).
 * getAllFAQ in the original defaults to {@code skip=0, limit=10}, so list endpoints page at 10.
 */
@Service
public class FaqService {

    /** services/faq.services.js getAllFAQ default: {@code { skip=0, limit=10 }}. */
    private static final Pageable DEFAULT_PAGE = PageRequest.of(0, 10);

    private final FaqRepository faqRepository;
    private final AdminRepository adminRepository;
    private final ActivityLogger activityLogger;

    public FaqService(FaqRepository faqRepository, AdminRepository adminRepository, ActivityLogger activityLogger) {
        this.faqRepository = faqRepository;
        this.adminRepository = adminRepository;
        this.activityLogger = activityLogger;
    }

    @Transactional
    public Map<String, Object> addFaq(AddFaqRequest req) {
        Admin admin = currentAdminOrThrow();
        faqRepository.findByQuestionAndDeletedFalse(req.getQuestion()).ifPresent(f -> {
            throw new ApiException(StatusCode.BAD_REQUEST, "Question must be unique");
        });
        Faq faq = new Faq();
        faq.setQuestion(req.getQuestion() == null ? "" : req.getQuestion());
        faq.setAnswer(req.getAnswer() == null ? "" : req.getAnswer());
        faq.setType(req.getType());
        Faq saved = faqRepository.save(faq);
        activityLogger.log(admin.getId(), "add_faq", null);
        return DocView.faq(saved);
    }

    @Transactional
    public String editFaq(EditFaqRequest req) {
        UUID id = parseUuid(req.getId());

        // Original pre-check: getFAQ({ question, delete:false, id:{$nin:[id]} }) -> "FAQ Already exist".
        // Only meaningful when a question is supplied (the "delete" action forbids it in validation).
        if (req.getQuestion() != null && !req.getQuestion().isBlank()) {
            faqRepository.findByQuestionAndDeletedFalseAndIdNot(req.getQuestion(), id).ifPresent(f -> {
                throw new ApiException(StatusCode.BAD_REQUEST, "FAQ Already exist");
            });
        }

        Admin admin = currentAdminOrThrow();

        if ("delete".equals(req.getAction())) {
            Faq faq = faqRepository.findById(id)
                    .orElseThrow(() -> new ApiException(StatusCode.BAD_REQUEST, "FAQ not deleted"));
            faq.setDeleted(true);
            faqRepository.save(faq);
            activityLogger.log(admin.getId(), "delete_faq", null);
            return "Deleted successfully";
        }
        if ("edit".equals(req.getAction())) {
            faqRepository.findByQuestionAndDeletedFalseAndIdNot(req.getQuestion(), id).ifPresent(f -> {
                throw new ApiException(StatusCode.BAD_REQUEST, "Question must be unique");
            });
            Faq faq = faqRepository.findById(id)
                    .orElseThrow(() -> new ApiException(StatusCode.BAD_REQUEST, "FAQ not updated"));
            faq.setQuestion(req.getQuestion());
            faq.setAnswer(req.getAnswer());
            faq.setType(req.getType());
            faqRepository.save(faq);
            activityLogger.log(admin.getId(), "edit_faq", null);
            return "Updated successfully";
        }
        throw new ApiException(StatusCode.BAD_REQUEST, "Invalid action");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getFaq(String id) {
        currentAdminOrThrow();
        Faq faq = faqRepository.findById(parseUuid(id))
                .orElseThrow(() -> new ApiException(StatusCode.BAD_REQUEST, "Invalid faq Id"));
        return DocView.faq(faq);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAllFaq() {
        // Original returns { document, total }; its "not available" guard checks `.length` on
        // that object (always undefined) so it never actually fires - preserve that.
        Page<Faq> page = faqRepository.findByDeletedFalse(DEFAULT_PAGE);
        return documentAndTotal(page.getContent(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getFaqByType(String type) {
        Page<Faq> page = faqRepository.findByTypeAndDeletedFalse(type, DEFAULT_PAGE);
        return documentAndTotal(page.getContent(), page.getTotalElements());
    }

    /* -------------------------------------------------------------------- helpers */

    private Map<String, Object> documentAndTotal(List<Faq> docs, long total) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("document", docs.stream().map(DocView::faq).collect(Collectors.toList()));
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
            throw new ApiException(StatusCode.BAD_REQUEST, "Invalid faq Id");
        }
    }
}
