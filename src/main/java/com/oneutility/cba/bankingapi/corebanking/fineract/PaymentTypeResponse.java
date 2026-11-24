package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One of Fineract's {@code GET /paymenttypes}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentTypeResponse(long id, String name, String description) {
}
