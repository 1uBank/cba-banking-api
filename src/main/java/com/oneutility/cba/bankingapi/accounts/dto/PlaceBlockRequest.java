package com.oneutility.cba.bankingapi.accounts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code POST /v1/accounts/{accountNumber}/blocks}. */
public record PlaceBlockRequest(@NotNull BlockType type, @NotBlank @Size(max = 100) String reason) {
}
