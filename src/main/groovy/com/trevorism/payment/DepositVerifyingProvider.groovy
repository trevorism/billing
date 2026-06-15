package com.trevorism.payment

/**
 * A capability for providers that can verify a client-reported inbound deposit on-chain: that the referenced
 * transaction settled and paid at least the expected amount to the expected address (and destination tag,
 * where applicable). Used to confirm receives without the API custodying anything.
 */
interface DepositVerifyingProvider {

    boolean verifyDeposit(String reference, String expectedAddress, BigDecimal expectedAmount, Long expectedDestinationTag)

    /**
     * Whether this rail attributes inbound deposits by a destination tag (XRP shares one account across owners
     * and disambiguates by tag). When true, a receive without a tag cannot be safely confirmed. Default false
     * for address-per-owner rails like Cardano.
     */
    default boolean requiresDestinationTag() { return false }
}
