package com.oneutility.cba.bankingapi.corebanking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * A transaction that moved money on a savings account. {@code typeEnum} is Fineract's savings transaction type;
 * {@code transfer} is set when it is one side of an account transfer.
 */
public record CoreTransaction(long transactionId, long savingsId, String accountNumber, long clientId, int typeEnum,
        boolean credit, BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter, LocalDate date,
        OffsetDateTime createdOn, boolean reversed, boolean reversal, String transactionReference, String paymentType,
        String note, String virtualAccountType, CoreTransferSides transfer) {
}
