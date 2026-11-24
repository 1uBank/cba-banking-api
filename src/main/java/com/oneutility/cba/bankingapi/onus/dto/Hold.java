package com.oneutility.cba.bankingapi.onus.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

/** {@code Hold}: an amount held on an account. {@code transactionReference} is absent for holds placed elsewhere. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Hold(String transactionReference, long holdTransactionId, String accountNumber, long savingsId,
        long clientId, String amount, String reason, String placedBy, String status, OffsetDateTime createdDate,
        OffsetDateTime releasedDate) {
}
