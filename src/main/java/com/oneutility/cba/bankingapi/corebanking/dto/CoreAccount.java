package com.oneutility.cba.bankingapi.corebanking.dto;

import java.math.BigDecimal;

/**
 * A savings account as CBA-CORE holds it. {@code accountNumber} is its NUBAN; balances are live.
 *
 * @param currentBalance the ledger balance
 * @param availableBalance what can be spent now, after holds
 * @param blockReason the reason given when the account was last blocked; {@code null} when not blocked
 */
public record CoreAccount(long savingsId, String accountNumber, long clientId, String accountName, long productId,
        String productName, CoreAccountStatus status, String currency, BigDecimal currentBalance,
        BigDecimal availableBalance, CoreBlock block, String blockReason) {
}
