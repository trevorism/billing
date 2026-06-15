package com.trevorism.payment

/**
 * A capability for providers whose wallets sign by returning a witness set (e.g. Cardano CIP-30 wallets like
 * Lace) rather than a fully assembled signed transaction. The API keeps the unsigned transaction from the
 * prepare step and assembles it with the client's witness server-side, so the frontend needs no transaction
 * serialization library.
 */
interface WitnessAssemblingProvider {

    /**
     * Assemble the previously-prepared {@code unsignedPayload} with the client-supplied {@code witness} and
     * broadcast the result.
     */
    LedgerSubmission submitSignedWitness(String unsignedPayload, String witness)
}
