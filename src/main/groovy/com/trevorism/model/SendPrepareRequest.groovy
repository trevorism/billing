package com.trevorism.model

import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

/**
 * Phase 1 of a non-custodial send: the client asks the API to build an unsigned transaction that they will
 * sign with their own wallet. The API never sees a private key — only the client's public {@code senderAddress}.
 */
@Introspected
@Schema(description = "A request to build an unsigned payment for the client to sign")
class SendPrepareRequest {

    @Schema(description = "The client's own wallet address that the payment is sent from (public; never a key)")
    String senderAddress
    @Schema(description = "Id of the recipient's payment method; its provider determines the rail")
    String paymentMethodId
    @Schema(description = "Amount to send, in the rail's native unit (XRP, ADA, ...)")
    BigDecimal amount
    @Schema(description = "Currency code")
    String currency
    @Schema(description = "Free form description of the payment")
    String description
}
