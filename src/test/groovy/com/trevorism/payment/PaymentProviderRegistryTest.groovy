package com.trevorism.payment

import com.trevorism.model.PaymentMethod
import com.trevorism.model.PaymentRequest
import com.trevorism.model.PaymentResult
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertThrows

class PaymentProviderRegistryTest {

    private PaymentProvider fakeProvider(String name) {
        return new PaymentProvider() {
            @Override String getName() { return name }
            @Override PaymentResult receiveMoney(PaymentRequest request, PaymentMethod method) { return null }
        }
    }

    @Test
    void testResolvesByNameCaseInsensitive() {
        PaymentProviderRegistry registry = new PaymentProviderRegistry([fakeProvider("stripe"), fakeProvider("xrp")])
        assert registry.get("stripe").name == "stripe"
        assert registry.get("XRP").name == "xrp"
        assert registry.availableProviders.containsAll(["stripe", "xrp"])
    }

    @Test
    void testUnknownProviderThrows() {
        PaymentProviderRegistry registry = new PaymentProviderRegistry([fakeProvider("xrp")])
        assertThrows(IllegalArgumentException) { registry.get("paypal") }
    }

    @Test
    void testNullProviderThrows() {
        PaymentProviderRegistry registry = new PaymentProviderRegistry([fakeProvider("xrp")])
        assertThrows(IllegalArgumentException) { registry.get(null) }
    }
}
