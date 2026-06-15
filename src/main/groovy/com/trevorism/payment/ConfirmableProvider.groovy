package com.trevorism.payment

/**
 * A capability for providers that can report whether a previously-broadcast transaction has reached a final
 * (validated) state on its ledger. A broadcast result like "tesSUCCESS"/"SUBMITTED" only means the node
 * accepted the transaction; this confirms it actually settled.
 */
interface ConfirmableProvider {

    /**
     * @param reference the on-chain transaction reference (hash) recorded at submit
     * @return "CONFIRMED" (settled in a validated ledger/block), "FAILED" (validated but rejected), or
     *         "PENDING" (not yet final / not found)
     */
    String checkStatus(String reference)
}
