package com.oneutility.cba.bankingapi.virtualaccounts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code POST /v1/virtual-accounts}. {@code accountNumber} is the phone number without its leading 0, for {@code AIRTIME} only. */
public record CreateVirtualAccountRequest(@NotBlank @Size(max = 50) String label,
        @NotNull @Pattern(regexp = "^\\d{10}$", message = "must be 10 digits") String parentAccountNumber,
        @NotNull VirtualAccountType type,
        @Pattern(regexp = "^\\d{10}$", message = "must be 10 digits") String accountNumber,
        @Pattern(regexp = "^[A-Za-z0-9_-]{1,50}$", message = "must be 1 to 50 letters, digits, _ or -") String transactionReference) {
}
