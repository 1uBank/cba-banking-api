package com.oneutility.cba.bankingapi.customers.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** {@code PUT /v1/customers/{clientId}/kyc-level}. {@code clientId}, if sent, must match the path. */
public record SetKycLevelRequest(@NotNull @Min(1) Integer kycLevel, Long clientId) {
}
