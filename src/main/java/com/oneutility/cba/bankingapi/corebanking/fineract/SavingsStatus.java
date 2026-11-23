package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A savings account's status: {@code code} is for example {@code savingsAccountStatusType.active}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SavingsStatus(Long id, String code, String value, boolean active, boolean closed) {
}
