package com.trevorism.payment

import groovy.transform.Canonical

/**
 * Normalized details of an on-chain deposit, fetched from a client-reported transaction hash and used to
 * verify that an inbound payment actually matches what a receive expected.
 */
@Canonical
class DepositDetails {

    /** Whether the deposit is settled (validated ledger / present in a block). */
    boolean settled

    /** The address the deposit was paid to. */
    String destinationAddress

    /** Destination tag (XRP), or null where the chain has no such concept. */
    Long destinationTag

    /** Amount paid to {@link #destinationAddress}, in the rail's native major unit (XRP/ADA). */
    BigDecimal amount
}
