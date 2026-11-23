package com.oneutility.cba.bankingapi.accounts.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** {@code POST /v1/accounts/search}: the BVN is in the body so it stays out of URLs. */
public record AccountSearchRequest(@NotNull @Pattern(regexp = "^\\d{11}$", message = "must be exactly 11 digits") String bvn) {
}
