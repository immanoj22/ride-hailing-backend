package com.ridehail.userservice.dto;

/**
 * Result of AdminUserReportService#exportReport - either a generated file (csv/pdf/excel bytes +
 * content type + filename) or one of the original's two raw, unencrypted
 * {@code res.status(400).json({message})} error bodies (export_report in user.controller.js
 * bypasses sendResponse entirely for file downloads, so those two error paths were never wrapped
 * in the standard status/success/data envelope either - preserved here on purpose).
 */
public record ExportReportResult(byte[] body, String contentType, String filename, boolean success, String errorMessage) {

    public static ExportReportResult of(byte[] body, String contentType, String filename) {
        return new ExportReportResult(body, contentType, filename, true, null);
    }

    public static ExportReportResult error(String message) {
        return new ExportReportResult(null, null, null, false, message);
    }
}
