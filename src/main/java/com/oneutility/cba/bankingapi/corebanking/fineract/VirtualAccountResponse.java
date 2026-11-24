package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;

/** A static virtual account as 1UBank's {@code /oneubank/virtual-accounts} returns it. Times are UTC. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VirtualAccountResponse(long savingsId, String accountNumber, String name, String label, String type,
        long parentSavingsId, String parentAccountNumber, long clientId, String status, String transactionReference,
        OffsetDateTime createdOn, OffsetDateTime updatedOn, OffsetDateTime closedOn) {
}
