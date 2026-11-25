package com.ridehail.userservice.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Port of helpers/user.commonFunction.js's getRequestMeta(req), used by logActivity to populate
 * the activity log's ip/device columns.
 */
public final class RequestMetaUtil {

    private RequestMetaUtil() {
    }

    public record RequestMeta(String ip, String device) {
    }

    public static RequestMeta extract(HttpServletRequest request) {
        if (request == null) {
            return new RequestMeta("", "");
        }
        String forwardedFor = request.getHeader("x-forwarded-for");
        String ip;
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            ip = forwardedFor.split(",")[0].trim();
        } else {
            String remoteAddress = request.getRemoteAddr();
            ip = remoteAddress != null ? remoteAddress : "";
        }
        String device = request.getHeader("user-agent");
        return new RequestMeta(ip, device != null ? device : "");
    }
}
