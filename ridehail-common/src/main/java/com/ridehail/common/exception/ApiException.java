package com.ridehail.common.exception;

/** Thrown by service-layer code to short-circuit straight to an error response, mirroring the
 *  many {@code return sendResponse(res, false, ...)} early-returns in the legacy controllers. */
public class ApiException extends RuntimeException {

    private final int statusCode;

    public ApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
