package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One of Fineract's {@code GET /codes}: a code (dropdown) list. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CodeResponse(long id, String name) {
}
