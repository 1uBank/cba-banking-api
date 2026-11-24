package com.oneutility.cba.bankingapi.corebanking.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * A hold on an account. {@code transactionReference} is the reference it was placed with; holds placed in the back
 * office or before the Banking API have none.
 */
public record CoreHold(long holdTransactionId, long savingsId, String accountNumber, long clientId, BigDecimal amount,
        String reason, String transactionReference, OffsetDateTime createdOn, boolean released,
        OffsetDateTime releasedOn) {
}
