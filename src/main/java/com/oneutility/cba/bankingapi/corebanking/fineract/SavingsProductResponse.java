package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One of Fineract's {@code GET /savingsproducts}, with 1UBank's NUBAN prefix and tier-limit flag. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SavingsProductResponse(long id, String name, String shortName, String description,
        CurrencyResponse currency, String nubanPrefix, boolean enforceTierLimits, String virtualAccountType) {
}
