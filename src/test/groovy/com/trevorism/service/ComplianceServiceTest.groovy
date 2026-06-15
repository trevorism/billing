package com.trevorism.service

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertThrows

class ComplianceServiceTest {

    @Test
    void testEnforceLimitRejectsOverCap() {
        ComplianceService compliance = new ComplianceService([xrp: new BigDecimal("100")], [] as Set)
        assertThrows(IllegalArgumentException) { compliance.enforceLimit("xrp", new BigDecimal("100.01")) }
    }

    @Test
    void testEnforceLimitAllowsAtOrUnderCap() {
        ComplianceService compliance = new ComplianceService([xrp: new BigDecimal("100")], [] as Set)
        compliance.enforceLimit("xrp", new BigDecimal("100"))   // no exception
        compliance.enforceLimit("xrp", new BigDecimal("1"))
    }

    @Test
    void testZeroOrAbsentLimitIsUnlimited() {
        ComplianceService compliance = new ComplianceService([xrp: BigDecimal.ZERO], [] as Set)
        compliance.enforceLimit("xrp", new BigDecimal("1000000"))     // 0 = unlimited
        compliance.enforceLimit("cardano", new BigDecimal("1000000")) // absent = unlimited
    }

    @Test
    void testScreenBlocksDenylistedAddressCaseInsensitive() {
        ComplianceService compliance = new ComplianceService([:], ["rbad"] as Set)
        assertThrows(IllegalArgumentException) { compliance.screen("rBad") }
    }

    @Test
    void testScreenAllowsCleanAddress() {
        ComplianceService compliance = new ComplianceService([:], ["rbad"] as Set)
        compliance.screen("rGood")   // no exception
        compliance.screen(null)
    }
}
