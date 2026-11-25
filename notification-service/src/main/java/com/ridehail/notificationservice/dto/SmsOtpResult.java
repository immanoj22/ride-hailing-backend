package com.ridehail.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Shape of the Twilio response the original controller returned as-is (`{sid, status}`). */
@Getter
@AllArgsConstructor
public class SmsOtpResult {
    private final String sid;
    private final String status;
}
