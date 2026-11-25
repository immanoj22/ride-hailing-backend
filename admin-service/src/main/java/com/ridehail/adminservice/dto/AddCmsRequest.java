package com.ridehail.adminservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Joi {@code addcmsValidation} (validations/cms.validations.js). */
@Getter
@Setter
public class AddCmsRequest {

    @NotBlank
    @Size(min = 3, max = 100)
    private String name;

    @NotBlank
    @Pattern(regexp = "^[a-z0-9-]+$")
    private String slug;

    @NotBlank
    @Pattern(regexp = "^(about|privacy|terms|faq|contact|other)$")
    private String page;

    @NotNull
    private Integer type;

    private String link;

    private String question;

    private String answer;
}
