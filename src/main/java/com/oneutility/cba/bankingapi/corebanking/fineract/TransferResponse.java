package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 1UBank's {@code GET /oneubank/transfers}: an account transfer found by its reference. Times are UTC. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransferResponse(long transferId, String transactionReference, long fromSavingsId, String fromAccountNo,
        long fromClientId, long toSavingsId, String toAccountNo, long toClientId, String toClientName, BigDecimal amount,
        String description, OffsetDateTime createdOn, boolean reversed) {
}
