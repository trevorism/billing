package com.trevorism.model

import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

/**
 * Phase 1 response: the unsigned transaction the client must sign with their wallet, plus the
 * {@code transactionId} used to correlate the later submit call.
 */
@Introspected
@Schema(description = "An unsigned transaction for the client to sign")
class UnsignedTransaction {

    @Schema(description = "Correlation id; pass it back to /api/payment/send/submit with the signed transaction")
    String transactionId
    @Schema(description = "Provider that built the transaction")
    String provider
    @Schema(description = "The serialized unsigned transaction to sign (chain specific)")
    String unsignedPayload
    @Schema(description = "Format of the payload", example = "XRPL_JSON", allowableValues = ["XRPL_JSON", "CARDANO_CBOR"])
    String signingFormat
    @Schema(description = "How the client should sign and what to submit back")
    String instructions

    UnsignedTransaction() {}

    UnsignedTransaction(String provider, String unsignedPayload, String signingFormat, String instructions) {
        this.provider = provider
        this.unsignedPayload = unsignedPayload
        this.signingFormat = signingFormat
        this.instructions = instructions
    }
}
