package com.oneutility.cba.bankingapi.corebanking.dto;

import java.time.OffsetDateTime;

/**
 * A static virtual account: a savings account of the parent's customer. {@code name} is what payers see:
 * {@code <owner's name> - <label>}. {@code status} is {@code ACTIVE} or {@code CLOSED}.
 */
public record CoreVirtualAccount(long savingsId, String accountNumber, String name, String label, String type,
        long parentSavingsId, String parentAccountNumber, long clientId, String status, String transactionReference,
        OffsetDateTime createdOn, OffsetDateTime updatedOn) {
}
