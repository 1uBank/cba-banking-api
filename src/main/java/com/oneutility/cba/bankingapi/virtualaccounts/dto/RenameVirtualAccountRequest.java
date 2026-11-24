package com.oneutility.cba.bankingapi.virtualaccounts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code PATCH /v1/virtual-accounts/{accountNumber}}. */
public record RenameVirtualAccountRequest(@NotBlank @Size(max = 50) String label) {
}
