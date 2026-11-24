package com.oneutility.cba.bankingapi.corebanking.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** A transfer between two 1UBank accounts, as posted in CBA-CORE. {@code description} is the narration stored with it. */
public record CoreTransfer(long transferId, String transactionReference, long fromSavingsId, String fromAccountNumber,
        long fromClientId, long toSavingsId, String toAccountNumber, String toAccountName, BigDecimal amount,
        String description, OffsetDateTime createdOn, boolean reversed) {
}
