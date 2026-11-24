package com.oneutility.cba.bankingapi.corebanking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A debit (withdrawal) on an account, with the payment type that decided its ledger entry. */
public record CoreDebit(long transactionId, long savingsId, String accountNumber, String transactionReference,
        String paymentType, BigDecimal amount, String narration, LocalDate date, boolean reversed) {
}
