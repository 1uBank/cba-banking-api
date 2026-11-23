package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One of Fineract's {@code GET /codes/{id}/codevalues}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CodeValueResponse(long id, String name, Boolean active) {
}
