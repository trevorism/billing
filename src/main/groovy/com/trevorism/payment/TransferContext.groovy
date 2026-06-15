package com.trevorism.payment

import groovy.transform.Canonical

/**
 * The inputs a provider needs to build an unsigned transfer: the client's own sending address, the
 * recipient's on-chain address, and the amount. All public data — no keys.
 */
@Canonical
class TransferContext {

    String senderAddress
    String destinationAddress
    BigDecimal amount
}
