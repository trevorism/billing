package com.trevorism.security

import io.micronaut.security.authentication.Authentication
import org.junit.jupiter.api.Test

class TenantResolverTest {

    @Test
    void testNullAuthenticationYieldsDefaultEmptyTenant() {
        assert TenantResolver.resolve(null) == ""
    }

    @Test
    void testMissingTenantClaimYieldsDefaultEmptyTenant() {
        Authentication auth = Authentication.build("user-1", [:])
        assert TenantResolver.resolve(auth) == ""
    }

    @Test
    void testTenantClaimIsResolved() {
        Authentication auth = Authentication.build("user-1", ["tenant": "tenant-123"])
        assert TenantResolver.resolve(auth) == "tenant-123"
    }
}
