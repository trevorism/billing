package com.trevorism.controller

import com.trevorism.PropertiesProvider
import com.trevorism.model.PaymentRequest
import com.trevorism.model.PaymentResult
import com.trevorism.model.PaymentMethod
import com.trevorism.payment.CardanoPaymentProvider
import com.trevorism.payment.PaymentProvider
import com.trevorism.payment.PaymentProviderRegistry
import com.trevorism.payment.XrpPaymentProvider
import org.junit.jupiter.api.Test

class ConfigControllerTest {

    private PropertiesProvider props = [getProperty: { String key -> null }] as PropertiesProvider

    private PaymentProvider stripe() {
        return new PaymentProvider() {
            @Override String getName() { return "stripe" }
            @Override PaymentResult receiveMoney(PaymentRequest request, PaymentMethod method) { return null }
        }
    }

    @Test
    void testConfigListsTheFourCryptoNetworks() {
        PaymentProviderRegistry registry = new PaymentProviderRegistry([
                new XrpPaymentProvider("xrp-testnet", "testnet", "https://rpc"),
                new XrpPaymentProvider("xrp-mainnet", "mainnet", "https://rpc"),
                new CardanoPaymentProvider(props, "cardano-preprod", "preprod"),
                new CardanoPaymentProvider(props, "cardano-mainnet", "mainnet"),
                stripe()
        ])
        ConfigController controller = new ConfigController(registry)

        Map<String, Object> config = controller.config()
        List networks = config.networks as List

        assert networks.collect { it.key } == ["cardano-mainnet", "cardano-preprod", "xrp-mainnet", "xrp-testnet"]
        assert networks.every { it.chain && it.walletNetwork && it.label }
        assert !networks.any { it.key == "stripe" }   // Stripe is not network-aware

        Map xrpMainnet = networks.find { it.key == "xrp-mainnet" } as Map
        assert xrpMainnet.chain == "xrp"
        assert xrpMainnet.walletNetwork == "mainnet"

        Map cardanoPreprod = networks.find { it.key == "cardano-preprod" } as Map
        assert cardanoPreprod.chain == "cardano"
        assert cardanoPreprod.walletNetwork == "testnet"
    }
}
