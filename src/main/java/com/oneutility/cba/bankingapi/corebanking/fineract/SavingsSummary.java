package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/** A savings account's balances. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SavingsSummary(BigDecimal accountBalance, BigDecimal availableBalance) {
}
