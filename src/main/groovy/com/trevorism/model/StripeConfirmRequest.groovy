package com.trevorism.model

import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

/**
 * Notification that a Stripe Checkout session completed. Sent by the trevorism Stripe service (which verifies
 * the Stripe webhook signature) so billing can confirm the matching receive. Billing cannot verify Stripe
 * signatures itself, so this is a trusted internal call rather than a raw Stripe webhook.
 */
@Introspected
@Schema(description = "A completed Stripe Checkout session notification")
class StripeConfirmRequest {

    @Schema(description = "The Stripe Checkout session id (recorded as the receive's externalReference)")
    String sessionId
}
