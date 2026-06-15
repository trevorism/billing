package com.trevorism.payment

import com.trevorism.https.SecureHttpClient
import com.trevorism.model.PaymentMethod
import com.trevorism.model.PaymentRequest
import com.trevorism.model.PaymentResult
import com.trevorism.model.TransactionStatus
import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Receives money by delegating to the deployed trevorism Stripe service, which creates a Stripe Checkout
 * Session to charge the customer.
 *
 * Stripe is intentionally not a {@link SignableTransferProvider}: fiat payouts are custodial (they move
 * funds from the platform's own Stripe balance using the platform's Stripe key — there is no client wallet
 * to sign), so they do not fit the non-custodial send flow. Sending via Stripe therefore returns 501 until a
 * dedicated, platform-custodial payout feature is added.
 */
@Singleton
class StripePaymentProvider implements PaymentProvider {

    static final String NAME = "stripe"
    private static final Logger log = LoggerFactory.getLogger(StripePaymentProvider)

    private final SecureHttpClient httpClient
    private final String stripeUrl
    private final ObjectMapper objectMapper = new ObjectMapper()

    StripePaymentProvider(SecureHttpClient httpClient, @Value('${stripe.url}') String stripeUrl) {
        this.httpClient = httpClient
        this.stripeUrl = stripeUrl
    }

    @Override
    String getName() {
        return NAME
    }

    @Override
    PaymentResult receiveMoney(PaymentRequest request, PaymentMethod paymentMethod) {
        if (!NAME.equalsIgnoreCase(paymentMethod?.provider)) {
            throw new IllegalArgumentException("StripePaymentProvider requires a stripe payment method")
        }
        String url = "${stripeUrl}/api/payment/session"
        Map sessionRequest = [
                name              : request.description ?: "Payment",
                dollars           : request.amount,
                successCallbackUrl: request.successCallbackUrl ?: "https://trevorism.com",
                failureCallbackUrl: request.failureCallbackUrl ?: "https://trevorism.com/contact"
        ]
        log.info("Creating Stripe checkout session at {} for {} dollars", url, request.amount)
        String response = httpClient.post(url, objectMapper.writeValueAsString(sessionRequest))
        String sessionId = objectMapper.readValue(response, Map)["id"]

        PaymentResult result = new PaymentResult(NAME, TransactionStatus.PENDING)
        result.externalReference = sessionId
        result.instructions = "Complete the payment using Stripe Checkout session id ${sessionId}".toString()
        return result
    }
}
