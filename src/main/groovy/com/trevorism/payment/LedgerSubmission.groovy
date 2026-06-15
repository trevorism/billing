package com.trevorism.payment

import groovy.transform.Canonical

/**
 * The normalized outcome of submitting a transaction to a blockchain, returned by a provider's
 * {@code submitPayment} seam. Isolating the chain/library specifics behind this small value object lets
 * unit tests demonstrate a provider end to end by overriding the seam, with no wallet, network, or money.
 */
@Canonical
class LedgerSubmission {

    /** Provider specific reference for the submitted transaction (e.g. transaction hash). */
    String reference

    /** Provider specific status string (e.g. "tesSUCCESS" for XRP, "SUBMITTED" for Cardano). */
    String status
}
