package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A value of a code list, as Fineract embeds it in an answer. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CodeValueRef(Long id, String name) {
}
