package com.oneutility.cba.bankingapi.corebanking.dto;

/** A customer just created, and their first account. */
public record CreatedCustomer(long clientId, long savingsId) {
}
