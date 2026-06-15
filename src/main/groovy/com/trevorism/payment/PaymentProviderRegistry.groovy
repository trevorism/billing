package com.trevorism.payment

import jakarta.inject.Singleton

/**
 * Resolves a {@link PaymentProvider} by its name. All providers on the classpath are injected and indexed
 * so that controllers and services stay decoupled from concrete rails.
 */
@Singleton
class PaymentProviderRegistry {

    private final Map<String, PaymentProvider> providersByName

    PaymentProviderRegistry(List<PaymentProvider> providers) {
        this.providersByName = providers.collectEntries { [(it.name.toLowerCase()): it] }
    }

    PaymentProvider get(String name) {
        if (!name) {
            throw new IllegalArgumentException("A payment provider must be specified")
        }
        PaymentProvider provider = providersByName.get(name.toLowerCase())
        if (!provider) {
            throw new IllegalArgumentException("Unknown payment provider: ${name}. Available: ${providersByName.keySet()}")
        }
        return provider
    }

    Set<String> getAvailableProviders() {
        return providersByName.keySet()
    }

    Collection<PaymentProvider> getAll() {
        return providersByName.values()
    }
}
