package com.trevorism.payment

import com.trevorism.model.PaymentMethod
import com.trevorism.model.PaymentRequest
import com.trevorism.model.PaymentResult

/**
 * A payment rail. Receiving money never requires a key, so it lives here for every provider. Sending money
 * is rail specific: blockchain providers send non-custodially via {@link SignableTransferProvider} (the
 * client signs), so there is deliberately no custodial {@code sendMoney} on this interface.
 */
interface PaymentProvider {

    /**
     * @return the unique provider key, e.g. "stripe", "xrp", "cardano", matched against {@link PaymentMethod#getProvider}
     */
    String getName()

    /**
     * Collect money from the owner of the given payment method (no key required).
     *
     * @throws UnsupportedOperationException if this provider cannot receive money
     */
    PaymentResult receiveMoney(PaymentRequest request, PaymentMethod paymentMethod)
}
