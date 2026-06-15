package com.trevorism.payment

/**
 * A capability for crypto providers that are tied to a specific blockchain network. The deployed API runs one
 * provider instance per network (e.g. xrp-testnet, xrp-mainnet, cardano-preprod, cardano-mainnet); this
 * exposes the metadata the frontend needs to render a network picker, filter wallets by chain, and guard the
 * wallet's connected network. Stripe is not network-aware, so it does not implement this.
 */
interface NetworkAwareProvider {

    /** Chain this provider serves: "xrp" or "cardano". Drives wallet filtering and the client sign path. */
    String getChain()

    /**
     * Coarse wallet network the user's wallet must be on: "mainnet" or "testnet".
     */
    String getWalletNetwork()

    /** Human-readable label for the picker, e.g. "XRP Testnet". */
    String getLabel()
}
