package com.trevorism.model

/**
 * The canonical lifecycle states of a {@link Transaction}. Centralizing these (instead of scattering string
 * literals) keeps the state machine consistent: providers normalize chain results to these values, and the
 * service's terminal/replay decisions key off them.
 *
 *   PREPARED  -> SUBMITTED -> CONFIRMED | FAILED
 *   PREPARED  -> EXPIRED
 *   (receive) PENDING -> CONFIRMED
 */
class TransactionStatus {

    static final String PREPARED = "PREPARED"
    static final String SUBMITTED = "SUBMITTED"
    static final String CONFIRMED = "CONFIRMED"
    static final String FAILED = "FAILED"
    static final String EXPIRED = "EXPIRED"
    static final String PENDING = "PENDING"

    /** Terminal states never change again and are never re-broadcast/re-checked. */
    static boolean terminal(String status) {
        return status in [CONFIRMED, FAILED, EXPIRED]
    }
}
