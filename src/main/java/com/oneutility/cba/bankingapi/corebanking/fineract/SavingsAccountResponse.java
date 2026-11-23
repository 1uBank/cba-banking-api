package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Fineract's {@code GET /savingsaccounts/{id}}, with the fields the Banking API uses. {@code accountNo} is the NUBAN. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SavingsAccountResponse(long id, String accountNo, Long clientId, String clientName,
        Long savingsProductId, String savingsProductName, SavingsStatus status, SavingsSubStatus subStatus,
        CurrencyResponse currency, SavingsSummary summary) {
}
