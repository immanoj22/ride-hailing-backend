package com.ridehail.driverservice.dto;

/**
 * Result of DriverReportService#exportReport - either a generated file (csv/pdf/excel bytes +
 * content type + filename) or the original's raw, unencrypted {@code res.status(400).json({message})}
 * error body (export_report in driver.controller.js bypasses sendResponse entirely for file
 * downloads, so that error path was never wrapped in the standard status/success/data envelope
 * either - preserved here on purpose).
 */
public record DriverExportReportResult(byte[] body, String contentType, String filename, boolean success, String errorMessage) {

    public static DriverExportReportResult of(byte[] body, String contentType, String filename) {
        return new DriverExportReportResult(body, contentType, filename, true, null);
    }

    public static DriverExportReportResult error(String message) {
        return new DriverExportReportResult(null, null, null, false, message);
    }
}
