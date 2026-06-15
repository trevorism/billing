package com.trevorism.payment

import org.junit.jupiter.api.Test

class DepositMatcherTest {

    private DepositDetails deposit(boolean settled, String addr, Long tag, String amount) {
        return new DepositDetails(settled, addr, tag, amount == null ? null : new BigDecimal(amount))
    }

    @Test
    void testMatchesExactAndOverpayment() {
        assert DepositMatcher.matches(deposit(true, "rDest", 7L, "5"), "rDest", new BigDecimal("5"), 7L)
        assert DepositMatcher.matches(deposit(true, "rDest", 7L, "6"), "rDest", new BigDecimal("5"), 7L)   // overpayment ok
    }

    @Test
    void testRejectsUnderpayment() {
        assert !DepositMatcher.matches(deposit(true, "rDest", 7L, "4.99"), "rDest", new BigDecimal("5"), 7L)
    }

    @Test
    void testRejectsWrongAddress() {
        assert !DepositMatcher.matches(deposit(true, "rOther", 7L, "5"), "rDest", new BigDecimal("5"), 7L)
    }

    @Test
    void testRejectsWrongDestinationTag() {
        assert !DepositMatcher.matches(deposit(true, "rDest", 999L, "5"), "rDest", new BigDecimal("5"), 7L)
    }

    @Test
    void testRejectsUnsettledOrNull() {
        assert !DepositMatcher.matches(deposit(false, "rDest", 7L, "5"), "rDest", new BigDecimal("5"), 7L)
        assert !DepositMatcher.matches(null, "rDest", new BigDecimal("5"), 7L)
    }

    @Test
    void testIgnoresTagWhenNotExpected() {
        // Cardano has no destination tag: a null expected tag must not block a match.
        assert DepositMatcher.matches(deposit(true, "addr_test1xyz", null, "10"), "addr_test1xyz", new BigDecimal("10"), null)
    }
}
