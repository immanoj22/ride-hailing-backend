package com.ridehail.notificationservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.notificationservice.dto.SmsOtpResult;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

/**
 * Direct port of controllers/notification.controller.js's {@code sendSmsOtp} +
 * config/twillio.js's {@code sendSms}.
 */
@Service
public class SmsOtpService {

    private static final Logger log = LoggerFactory.getLogger(SmsOtpService.class);

    private final String accountSid;
    private final String authToken;
    private final String messagingServiceSid;

    public SmsOtpService(
            @Value("${ridehail.twilio.account-sid:}") String accountSid,
            @Value("${ridehail.twilio.auth-token:}") String authToken,
            @Value("${ridehail.twilio.messaging-service-sid:}") String messagingServiceSid) {
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.messagingServiceSid = messagingServiceSid;
    }

    public SmsOtpResult sendSmsOtp(String mobile, String otp) {
        String normalizedMobile = mobile == null ? "" : mobile.trim();
        String phone = normalizedMobile.startsWith("+") ? normalizedMobile : "+91" + normalizedMobile;
        String message = "Your OTP for RideHail is " + otp + ". Please do not share this OTP with anyone.";

        try {
            return sendSms(phone, message);
        } catch (ApiException e) {
            throw e;
        } catch (Exception err) {
            log.error("error from sendSmsOtp", err);
            throw new ApiException(StatusCode.BAD_GATEWAY, err.getMessage() != null ? err.getMessage() : "Failed to send OTP");
        }
    }

    /** Mirrors config/twillio.js's {@code sendSms}. */
    private SmsOtpResult sendSms(String to, String body) {
        if (isBlank(accountSid) || isBlank(authToken) || isBlank(messagingServiceSid)) {
            throw new IllegalStateException("Twilio credentials are not configured");
        }

        Twilio.init(accountSid.trim(), authToken.trim());

        Message message = Message.creator(new PhoneNumber(to), messagingServiceSid.trim(), body).create();

        log.info("Twilio SMS response: sid={}, status={}", message.getSid(), message.getStatus());

        return new SmsOtpResult(message.getSid(), message.getStatus() == null ? null : message.getStatus().toString());
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
