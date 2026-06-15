package com.trevorism.payment

import com.trevorism.https.SecureHttpClient
import com.trevorism.model.PaymentMethod
import com.trevorism.model.PaymentRequest
import com.trevorism.model.PaymentResult
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertThrows

class StripePaymentProviderTest {

    @Test
    void testNameIsStripe() {
        StripePaymentProvider provider = new StripePaymentProvider(null, "https://stripe.trevorism.com")
        assert provider.name == "stripe"
    }

    @Test
    void testStripeIsNotSignableTransferProvider() {
        // Stripe payouts are custodial (no client wallet to sign), so Stripe must NOT advertise the
        // non-custodial send capability. The service maps this to 501 for send.
        StripePaymentProvider provider = new StripePaymentProvider(null, "https://stripe.trevorism.com")
        assert !(provider instanceof SignableTransferProvider)
    }

    @Test
    void testReceiveRejectsNonStripeMethod() {
        StripePaymentProvider provider = new StripePaymentProvider(null, "https://stripe.trevorism.com")
        assertThrows(IllegalArgumentException) {
            provider.receiveMoney(new PaymentRequest(amount: 5), new PaymentMethod(provider: "xrp"))
        }
    }

    @Test
    void testReceiveMoneyCreatesCheckoutSession() {
        String capturedUrl = null
        String capturedBody = null
        SecureHttpClient client = [
                post: { String url, String body ->
                    capturedUrl = url
                    capturedBody = body
                    return '{"id":"cs_test_123"}'
                }
        ] as SecureHttpClient

        StripePaymentProvider provider = new StripePaymentProvider(client, "https://stripe.trevorism.com")
        PaymentRequest request = new PaymentRequest(paymentMethodId: "pm1", amount: 25.00, description: "Order 7")
        PaymentResult result = provider.receiveMoney(request, new PaymentMethod(provider: "stripe", ownerId: "c1"))

        assert capturedUrl == "https://stripe.trevorism.com/api/payment/session"
        assert capturedBody.contains("25")
        assert result.provider == "stripe"
        assert result.status == "PENDING"
        assert result.externalReference == "cs_test_123"
        assert result.instructions.contains("cs_test_123")
    }
}
