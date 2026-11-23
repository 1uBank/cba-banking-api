package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/** One savings account in Fineract's {@code GET /clients/{id}/accounts}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientSavingsAccount(long id, String accountNo, Long productId, String productName,
        SavingsStatus status, CurrencyResponse currency, BigDecimal accountBalance) {
}
