package com.oneutility.cba.bankingapi.onus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code POST /v1/debits}. */
public record ServiceDebitRequest(@NotNull @Pattern(regexp = "^\\d{10}$", message = "must be 10 digits") String accountNumber,
        @NotBlank String paymentType,
        @NotNull @Pattern(regexp = "^(?=.*[1-9])\\d{1,15}(\\.\\d{1,2})?$", message = "must be an amount above zero with at most 2 decimal places") String amount,
        @Size(max = 100) String narration,
        @NotNull @Pattern(regexp = "^[A-Za-z0-9_-]{1,50}$", message = "must be 1 to 50 letters, digits, _ or -") String transactionReference) {
}
