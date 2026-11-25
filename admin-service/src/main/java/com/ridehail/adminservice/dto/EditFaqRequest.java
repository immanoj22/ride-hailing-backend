package com.ridehail.adminservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Joi {@code editfaqValidation} (validations/faq.validations.js). Per-action required/forbidden
 * rules are enforced in {@code FaqService.editFaq}.
 */
@Getter
@Setter
public class EditFaqRequest {

    @NotBlank
    @Pattern(regexp = "^(edit|delete)$")
    private String action;

    @NotBlank
    @Size(min = 3, max = 100)
    private String id;

    private String question;

    private String answer;

    private String type;
}
