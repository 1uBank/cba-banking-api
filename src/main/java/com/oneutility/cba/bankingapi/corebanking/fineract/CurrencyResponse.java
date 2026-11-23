package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A currency in a Fineract answer. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CurrencyResponse(String code, Integer decimalPlaces) {
}
