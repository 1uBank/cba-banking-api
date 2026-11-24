package com.oneutility.cba.bankingapi.onus.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

/** {@code ServiceDebit}: final, so always {@code CONFIRMED}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServiceDebit(String transactionReference, String transactionId, String accountNumber, String paymentType,
        String amount, String narration, String transactionStatus, OffsetDateTime transactionDate) {
}
