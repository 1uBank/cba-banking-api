package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A savings account's sub-status: dormant, or blocked for credits, debits or both. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SavingsSubStatus(Long id, String code, boolean dormant, boolean block, boolean blockCredit,
        boolean blockDebit) {
}
