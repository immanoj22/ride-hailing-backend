package com.ridehail.notificationservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ridehail.common.response.ApiResponseService;
import com.ridehail.notificationservice.dto.SendSmsOtpRequest;
import com.ridehail.notificationservice.dto.SmsOtpResult;
import com.ridehail.notificationservice.service.SmsOtpService;

import jakarta.validation.Valid;

/** Direct port of the {@code sendSmsOtp} controller function (POST /notification-apis/send-sms-otp). */
@RestController
@RequestMapping("/notification-apis")
public class SmsOtpController {

    private final SmsOtpService smsOtpService;
    private final ApiResponseService apiResponseService;

    public SmsOtpController(SmsOtpService smsOtpService, ApiResponseService apiResponseService) {
        this.smsOtpService = smsOtpService;
        this.apiResponseService = apiResponseService;
    }

    @PostMapping("/send-sms-otp")
    public ResponseEntity<?> sendSmsOtp(@Valid @RequestBody SendSmsOtpRequest request) {
        SmsOtpResult result = smsOtpService.sendSmsOtp(request.getMobile(), request.getOtp());
        return apiResponseService.ok("OTP sent successfully", result);
    }
}
