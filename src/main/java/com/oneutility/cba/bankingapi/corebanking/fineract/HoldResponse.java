package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 1UBank's {@code GET /oneubank/holds}: a hold and whether it has been released. Times are UTC. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HoldResponse(long holdTransactionId, long savingsId, String accountNo, Long clientId, BigDecimal amount,
        String reason, String transactionReference, OffsetDateTime createdOn, boolean released,
        Long releaseTransactionId, OffsetDateTime releasedOn, boolean reversed) {
}
