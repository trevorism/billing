package com.trevorism.model

import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

/**
 * Phase 2 of a non-custodial send: the client returns the transaction they signed with their wallet so the
 * API can broadcast it to the network.
 */
@Introspected
@Schema(description = "A client-signed transaction to broadcast")
class SignedSendRequest {

    @Schema(description = "The transactionId returned from /api/payment/send/prepare")
    String transactionId
    @Schema(description = "The signed transaction from the client's wallet (XRP tx_blob hex, or Cardano signed CBOR hex)")
    String signedTransaction
}
