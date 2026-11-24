package com.oneutility.cba.bankingapi.onus.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/** {@code POST /v1/holds/release}: either {@code transactionReference}, or {@code accountNumber} with {@code holdTransactionId}. */
public record ReleaseHoldRequest(
        @Pattern(regexp = "^[A-Za-z0-9_-]{1,50}$", message = "must be 1 to 50 letters, digits, _ or -") String transactionReference,
        @Pattern(regexp = "^\\d{10}$", message = "must be 10 digits") String accountNumber,
        @Positive Long holdTransactionId) {

    @JsonIgnore
    public boolean byReference() {
        return transactionReference != null && accountNumber == null && holdTransactionId == null;
    }

    @JsonIgnore
    public boolean byHoldTransactionId() {
        return transactionReference == null && accountNumber != null && holdTransactionId != null;
    }
}
