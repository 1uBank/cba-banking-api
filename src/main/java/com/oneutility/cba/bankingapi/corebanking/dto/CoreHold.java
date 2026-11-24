package com.oneutility.cba.bankingapi.corebanking.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * A hold on an account. A reversed hold (undone in the back office) no longer holds anything, like a released one. {@code transactionReference} is the reference it was placed with; holds placed in the back
 * office or before the Banking API have none. {@code placedByClient} is false for holds bank staff or compliance
 * placed, which clients cannot release.
 */
public record CoreHold(long holdTransactionId, long savingsId, String accountNumber, long clientId, BigDecimal amount,
        String reason, String transactionReference, OffsetDateTime createdOn, boolean released,
        OffsetDateTime releasedOn, boolean reversed, boolean placedByClient) {

    /** Whether the money is still held: neither released nor reversed. */
    public boolean active() {
        return !released && !reversed;
    }
}
