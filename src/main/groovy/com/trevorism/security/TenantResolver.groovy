package com.trevorism.security

import io.micronaut.security.authentication.Authentication

/**
 * Resolves the tenant that owns a record from the authenticated caller's token.
 *
 * The platform is currently single-tenant: when the token carries no tenant claim (or there is no
 * authenticated caller, e.g. local dev), records belong to the default tenant, represented by the empty
 * tenant guid. When the platform goes multi-tenant, the {@code tenant} claim populates and reads can be
 * filtered by it without changing how records are stamped.
 */
class TenantResolver {

    /** The default (single) tenant: the empty tenant guid. */
    static final String DEFAULT_TENANT = ""

    static String resolve(Authentication authentication) {
        Object tenant = authentication?.getAttributes()?.get("tenant")
        return tenant ? tenant.toString() : DEFAULT_TENANT
    }
}
