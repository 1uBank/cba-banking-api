package com.oneutility.cba.bankingapi.customers.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** {@code PUT /v1/customers/{clientId}/transaction-limit}. */
public record SetTransactionLimitRequest(@NotNull @Pattern(regexp = Patterns.AMOUNT, message = "must be an amount above zero with at most 2 decimal places") String transactionLimit) {
}
