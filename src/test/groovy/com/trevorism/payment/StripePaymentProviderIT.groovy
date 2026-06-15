package com.trevorism.payment

import com.trevorism.https.AppClientSecureHttpClient
import com.trevorism.model.PaymentRequest
import com.trevorism.model.PaymentResult
import com.trevorism.model.PaymentMethod
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Real integration test against the deployed trevorism Stripe service in TEST MODE (no real money is moved
 * as long as that service is configured with a Stripe test key). Tagged "integration" and excluded from the
 * normal build; run with: gradle integrationTest
 *
 * Requires:
 *  - valid trevorism clientId/clientSecret in src/main/resources/secrets.properties (used to authenticate)
 *  - env RUN_STRIPE_IT=true to opt in
 */
@Tag("integration")
class StripePaymentProviderIT {

    @Test
    void testCreatesRealStripeCheckoutSession() {
        assumeTrue(System.getenv("RUN_STRIPE_IT") == "true", "Set RUN_STRIPE_IT=true to run the Stripe integration test")

        StripePaymentProvider provider = new StripePaymentProvider(new AppClientSecureHttpClient(), "https://stripe.trevorism.com")
        PaymentRequest request = new PaymentRequest(amount: 1.00, description: "billing integration test")

        PaymentResult result = provider.receiveMoney(request, new PaymentMethod(provider: "stripe", ownerId: "it-customer"))

        assert result.provider == "stripe"
        assert result.externalReference != null
        println "Stripe test-mode checkout session: ${result.externalReference}"
    }
}
