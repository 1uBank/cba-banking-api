package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One of Fineract's {@code GET /clients/{id}/identifiers}. {@code status} is for example {@code clientIdentifierStatusType.active}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientIdentifierResponse(long id, CodeValueRef documentType, String documentKey, String status) {
}
