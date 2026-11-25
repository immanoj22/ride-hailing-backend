package com.ridehail.common.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.ridehail.common.constants.AuthUserType;

/**
 * Request-scoped holder for the identity resolved by the auth filter, equivalent to the
 * {@code req.auth_user} / {@code req.user} / {@code req.driver} / {@code req.admin} fields the
 * legacy Node auth middleware attached to the request object. Populated by each service's auth
 * filter and cleared at the end of every request.
 */
public final class AuthContext {

    private static final ThreadLocal<AuthContext> CURRENT = new ThreadLocal<>();

    private final AuthUserType authUserType;
    private final String principalId;
    private final JsonNode principal;

    private AuthContext(AuthUserType authUserType, String principalId, JsonNode principal) {
        this.authUserType = authUserType;
        this.principalId = principalId;
        this.principal = principal;
    }

    public static void set(AuthUserType authUserType, String principalId, JsonNode principal) {
        CURRENT.set(new AuthContext(authUserType, principalId, principal));
    }

    public static AuthContext current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public AuthUserType authUserType() {
        return authUserType;
    }

    /** The authenticated user/driver/admin id (JWT subject), or null for "other"/"inapp" auth. */
    public String principalId() {
        return principalId;
    }

    /** Raw JSON of the user/driver/admin record fetched from the owning service, if applicable. */
    public JsonNode principal() {
        return principal;
    }
}
