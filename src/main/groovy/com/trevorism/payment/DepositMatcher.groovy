package com.trevorism.payment

/**
 * Decides whether a fetched {@link DepositDetails} satisfies what a receive expected. This is the security
 * critical check for inbound payments, so it is a pure function (no chain access) and fully unit tested.
 */
class DepositMatcher {

    static boolean matches(DepositDetails deposit, String expectedAddress, BigDecimal expectedAmount, Long expectedDestinationTag) {
        if (deposit == null || !deposit.settled) {
            return false
        }
        if (!expectedAddress || deposit.destinationAddress != expectedAddress) {
            return false
        }
        if (expectedDestinationTag != null && deposit.destinationTag != expectedDestinationTag) {
            return false
        }
        return deposit.amount != null && expectedAmount != null && deposit.amount >= expectedAmount
    }
}
