package com.trevorism.controller

import com.trevorism.payment.NetworkAwareProvider
import com.trevorism.payment.PaymentProviderRegistry
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

/**
 * Public client configuration. The frontend reads the list of supported crypto networks so it can render a
 * network picker, filter wallets to the matching chain, and require the user's wallet to be on the matching
 * network before signing. The list is derived from the registered providers, so it stays in sync with what
 * the API can actually route.
 */
@Controller("/api/config")
class ConfigController {

    private final PaymentProviderRegistry registry

    ConfigController(PaymentProviderRegistry registry) {
        this.registry = registry
    }

    @Tag(name = "Config Operations")
    @Operation(summary = "Returns client configuration, including the supported crypto networks")
    @Get(produces = MediaType.APPLICATION_JSON)
    Map<String, Object> config() {
        List<Map<String, String>> networks = registry.all
                .findAll { it instanceof NetworkAwareProvider }
                .collect { provider ->
                    NetworkAwareProvider n = (NetworkAwareProvider) provider
                    [key: provider.name, chain: n.chain, walletNetwork: n.walletNetwork, label: n.label]
                }
                .sort { it.key }
        return [networks: networks]
    }
}
