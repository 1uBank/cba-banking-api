package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Fineract's {@code GET /clients/{id}/accounts?fields=savingsAccounts}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientAccountsResponse(List<ClientSavingsAccount> savingsAccounts) {

    public ClientAccountsResponse {
        savingsAccounts = savingsAccounts == null ? List.of() : List.copyOf(savingsAccounts);
    }
}
