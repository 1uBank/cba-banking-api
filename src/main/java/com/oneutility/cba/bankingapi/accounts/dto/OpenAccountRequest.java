package com.oneutility.cba.bankingapi.accounts.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/** {@code POST /v1/customers/{clientId}/accounts}. */
public record OpenAccountRequest(@NotNull @Positive Long productId, Long clientId,
        @Pattern(regexp = "^[A-Za-z0-9_-]{1,50}$", message = "must be 1 to 50 letters, digits, _ or -") String transactionReference) {
}
