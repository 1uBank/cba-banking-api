package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** 1UBank's {@code GET /oneubank/transactions}: savings transactions that move money, newest first. Times are UTC. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionsResponse(List<Item> items, boolean hasMore) {

    public TransactionsResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(long transactionId, long savingsId, String accountNo, long clientId, int typeEnum, String entryType,
            BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter,
            @JsonDeserialize(using = FineractDates.class) LocalDate transactionDate, OffsetDateTime createdOn, boolean reversed,
            boolean reversal, String transactionReference, String paymentType, String note, String virtualAccountType,
            Transfer transfer) {
    }

    /** Both sides of the account transfer a transaction belongs to. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Transfer(long transferId, String fromAccountNo, long fromTransactionId, String fromName, String toAccountNo,
            long toTransactionId, String toName, String description) {
    }
}
