package com.ridehail.common.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.ridehail.common.constants.AuthUserType;

/**
 * Resolves the JWT subject to an active user/driver/admin record, mirroring the legacy per-service
 * auth middleware: a service checks its OWN domain type against its local database and calls out
 * to the owning service (via {@link InternalServiceClient}) for the other two types. Each service
 * supplies exactly one implementation covering all three (USER/DRIVER/ADMIN).
 */
public interface PrincipalLookupService {

    /** Returns the principal's JSON record if {@code id} is a known, active/enabled account of
     *  the given type, otherwise {@code null} (which the auth filter turns into a 401). */
    JsonNode lookup(AuthUserType type, String id);
}
