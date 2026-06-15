package com.trevorism.model

import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

/**
 * Phase 2 of a non-custodial send for CIP-30 wallets (e.g. Cardano / Lace): the client returns only the
 * witness set produced by {@code wallet.signTx(...)}, and the API assembles it with the unsigned transaction
 * it kept from prepare. No transaction-serialization library is needed in the frontend.
 */
@Introspected
@Schema(description = "A client wallet witness to assemble and broadcast")
class WitnessSubmitRequest {

    @Schema(description = "The transactionId returned from /api/payment/send/prepare")
    String transactionId
    @Schema(description = "The witness set hex returned by the wallet's signTx (CIP-30)")
    String witness
}
