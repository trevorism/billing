package com.trevorism.model

import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

/**
 * The request body for sending or receiving money. The {@code paymentMethodId} selects which payment
 * method (and therefore which provider) is used.
 */
@Introspected
@Schema(description = "A request to move money via a payment method")
class PaymentRequest {

    @Schema(description = "Id of the payment method to use; its provider determines the rail")
    String paymentMethodId
    @Schema(description = "Amount of money to move, in the given currency (or XRP for the xrp provider)")
    BigDecimal amount
    @Schema(description = "Currency code", example = "USD")
    String currency
    @Schema(description = "Free form description of the payment")
    String description
    @Schema(description = "Stripe success redirect url (receive via stripe)")
    String successCallbackUrl
    @Schema(description = "Stripe failure redirect url (receive via stripe)")
    String failureCallbackUrl
}
