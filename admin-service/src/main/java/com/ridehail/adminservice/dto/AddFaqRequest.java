package com.ridehail.adminservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Joi {@code addfaqValidation} (validations/faq.validations.js). */
@Getter
@Setter
public class AddFaqRequest {

    private String question;

    private String answer;

    @NotBlank
    private String type;
}
