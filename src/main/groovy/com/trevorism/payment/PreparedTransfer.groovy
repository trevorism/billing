package com.trevorism.payment

import groovy.transform.Canonical

/**
 * A provider's output for phase 1 of a non-custodial send: the serialized unsigned transaction for the
 * client to sign, plus a format hint and human readable instructions.
 */
@Canonical
class PreparedTransfer {

    String unsignedPayload
    String signingFormat
    String instructions
}
