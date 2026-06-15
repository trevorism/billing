package com.trevorism.service

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Compliance gate for money movement: per-transaction limits and address screening.
 *
 * Screening here is a config-driven denylist — a seam for a real sanctions/AML provider (e.g. OFAC list,
 * Chainalysis) which would replace {@link #screen}. KYC, licensing, and reporting are policy/external
 * concerns outside this service.
 */
@Singleton
class ComplianceService {

    private final Map<String, BigDecimal> limitsByProvider
    private final Set<String> denylist

    @Inject
    ComplianceService(@Property(name = 'payment.limits') @Nullable Map<String, String> limits,
                      @Value('${payment.screening.denylist:}') List<String> denylist) {
        // Keyed by the provider's network-qualified name (xrp-testnet, cardano-mainnet, stripe, ...).
        this.limitsByProvider = ((limits ?: [:]).collectEntries { k, v ->
            [(k?.toString()?.toLowerCase()): new BigDecimal(v?.toString() ?: "0")]
        }) as Map<String, BigDecimal>
        this.denylist = ((denylist ?: []).collect { it?.trim()?.toLowerCase() }.findAll { it }) as Set
    }

    ComplianceService(Map<String, BigDecimal> limitsByProvider, Set<String> denylist) {
        this.limitsByProvider = limitsByProvider ?: [:]
        this.denylist = denylist ?: ([] as Set)
    }

    /** A no-limits, no-screening instance (defaults / tests). */
    static ComplianceService permissive() {
        return new ComplianceService([:], [] as Set)
    }

    /** A configured limit of 0 (or absent) means unlimited. */
    void enforceLimit(String provider, BigDecimal amount) {
        BigDecimal limit = limitsByProvider.get(provider)
        if (limit != null && limit.signum() > 0 && amount != null && amount > limit) {
            throw new IllegalArgumentException("Amount ${amount} exceeds the ${provider} per-transaction limit of ${limit}")
        }
    }

    void screen(String address) {
        if (address && denylist.contains(address.toLowerCase())) {
            throw new IllegalArgumentException("Address is blocked by screening: ${address}")
        }
    }
}
