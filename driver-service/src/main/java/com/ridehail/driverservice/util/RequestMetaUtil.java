package com.ridehail.driverservice.util;

import jakarta.servlet.http.HttpServletRequest;

/** Equivalent of helpers/driver.commonFunction.js's getRequestMeta(req). */
public final class RequestMetaUtil {

    private RequestMetaUtil() {
    }

    public static String ip(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String forwardedFor = request.getHeader("x-forwarded-for");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr == null ? "" : remoteAddr;
    }

    public static String device(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String userAgent = request.getHeader("user-agent");
        return userAgent == null ? "" : userAgent;
    }
}
