package com.oneutility.cba.bankingapi.onus.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

/** {@code Transfer}: an intra-bank transfer is final, so always {@code CONFIRMED}, with no fee. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Transfer(String transactionReference, String transactionId, String transferType, String transactionStatus,
        String sourceAccountNumber, String destinationInstitutionCode, String destinationAccountNumber,
        String destinationAccountName, String amount, String fee, String currency, String narration,
        OffsetDateTime transactionDate) {
}
