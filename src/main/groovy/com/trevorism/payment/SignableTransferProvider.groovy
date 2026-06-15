package com.trevorism.payment

/**
 * A capability for providers that move money non-custodially: the API builds an unsigned transaction, the
 * client signs it with their own wallet, and the API broadcasts the signed result. The API never holds a
 * private key. Implemented by the blockchain providers (XRP, Cardano); fiat rails like Stripe do not.
 */
interface SignableTransferProvider {

    /** Phase 1: build an unsigned transaction for the client to sign. */
    PreparedTransfer prepareTransfer(TransferContext context)

    /**
     * Phase 2: verify the client-signed transaction matches the one we prepared ({@code unsignedPayload}),
     * then broadcast it and return its reference and status.
     */
    LedgerSubmission submitSignedTransfer(String unsignedPayload, String signedTransaction)
}
