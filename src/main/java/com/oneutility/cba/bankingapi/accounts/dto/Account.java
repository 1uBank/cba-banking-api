package com.oneutility.cba.bankingapi.accounts.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** {@code Account}: an account and its live balances. Amounts are strings with 2 decimal places. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Account(String accountNumber, long savingsId, long clientId, String accountName, long productId,
        String productName, AccountType accountType, String status, String currency, String currentBalance,
        String availableBalance, Integer kycLevel) {
}
