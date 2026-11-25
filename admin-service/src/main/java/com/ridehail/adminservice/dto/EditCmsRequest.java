package com.ridehail.adminservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * Joi {@code editcmsValidation} (validations/cms.validations.js). The per-action required/forbidden
 * rules are enforced in {@code CmsService.editCms} to mirror the original {@code when(action)} logic.
 */
@Getter
@Setter
public class EditCmsRequest {

    @NotBlank
    @Pattern(regexp = "^(edit|delete)$")
    private String action;

    @NotBlank
    private String id;

    private String name;

    private String slug;

    private String page;

    private Integer type;

    private String link;

    private String question;

    private String answer;
}
