package com.oneutility.cba.bankingapi.corebanking.dto;

/** Both sides of an account transfer, with each account's owner name and the narration stored with it. */
public record CoreTransferSides(long transferId, String fromAccountNumber, long fromTransactionId, String fromName,
        String toAccountNumber, long toTransactionId, String toName, String description) {
}
