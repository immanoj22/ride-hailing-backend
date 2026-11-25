package com.ridehail.common.constants;

import java.util.Arrays;
import java.util.Optional;

/**
 * Mirrors the legacy Node AUTH_USER_TYPES map: the "user-auth-type" request header selects which
 * JWT secret / identity-lookup path the auth filter uses.
 */
public enum AuthUserType {

    USER("ridehail-user"),
    DRIVER("ridehail-driver"),
    OTHER("ridehail-other"),
    IN_APP("ridehail-in-app"),
    ADMIN("ridehail-admin");

    private final String headerValue;

    AuthUserType(String headerValue) {
        this.headerValue = headerValue;
    }

    public String headerValue() {
        return headerValue;
    }

    public static Optional<AuthUserType> fromHeaderValue(String headerValue) {
        return Arrays.stream(values())
                .filter(t -> t.headerValue.equalsIgnoreCase(headerValue))
                .findFirst();
    }
}
