package com.oneutility.cba.bankingapi.customers.dto;

/** A customer's own transaction limit, as a two-decimal string. */
public record TransactionLimit(long clientId, String transactionLimit) {
}
