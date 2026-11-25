package com.ridehail.notificationservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body for POST /send-sms-otp.
 *
 * <p>Mirrors validations/mailTemplateValidation.js's {@code sendSmsOtpSchema}: both
 * {@code mobile} and {@code otp} are required, trimmed strings (Joi's {@code .trim()} only
 * validates that the value has no leading/trailing whitespace when combined with Joi's default
 * convert=true it actually trims the value; the original controller re-trims defensively anyway,
 * which {@link com.ridehail.notificationservice.service.SmsOtpService} reproduces).
 */
@Getter
@Setter
public class SendSmsOtpRequest {

    @NotBlank(message = "\"mobile\" is not allowed to be empty")
    private String mobile;

    @NotBlank(message = "\"otp\" is not allowed to be empty")
    private String otp;
}
