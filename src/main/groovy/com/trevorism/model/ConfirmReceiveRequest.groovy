package com.trevorism.model

import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

/**
 * Confirms a receive by reporting the on-chain transaction the customer used to pay. The API verifies it
 * settled and paid the expected address/amount before marking the receive CONFIRMED.
 */
@Introspected
@Schema(description = "A client-reported deposit transaction to verify against a pending receive")
class ConfirmReceiveRequest {

    @Schema(description = "The on-chain transaction hash of the customer's deposit")
    String depositTransaction
}
