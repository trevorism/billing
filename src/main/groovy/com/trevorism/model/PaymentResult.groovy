package com.trevorism.model

import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

/**
 * The normalized result of a send or receive operation, regardless of provider.
 */
@Introspected
@Schema(description = "The outcome of a payment operation")
class PaymentResult {

    @Schema(description = "Provider that handled the operation")
    String provider
    @Schema(description = "Status of the operation", example = "PENDING")
    String status
    @Schema(description = "Provider specific reference (Stripe session id or XRPL transaction hash)")
    String externalReference
    @Schema(description = "Url the caller should redirect to in order to complete the payment, if applicable")
    String redirectUrl
    @Schema(description = "Human readable instructions for completing the payment, if applicable")
    String instructions

    PaymentResult() {}

    PaymentResult(String provider, String status) {
        this.provider = provider
        this.status = status
    }
}
