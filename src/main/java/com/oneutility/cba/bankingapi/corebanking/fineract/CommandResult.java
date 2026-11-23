package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** What Fineract answers to a write: the IDs of what it created or changed. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CommandResult(Long resourceId, Long officeId, Long clientId, Long savingsId) {
}
