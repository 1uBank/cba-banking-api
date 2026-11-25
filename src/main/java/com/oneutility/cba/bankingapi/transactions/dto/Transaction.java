package com.oneutility.cba.bankingapi.transactions.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

/** {@code Transaction}: one posting on an account. Amounts are strings with 2 decimal places. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Transaction(String transactionId, String accountNumber, String transactionType, String kind, String amount,
        String currency, String balanceBefore, String balanceAfter, String narration, String transactionReference,
        String virtualAccountType, String paymentType, String transactionStatus, boolean reversed, boolean intraBank,
        TransferLegs transfer, Counterparty counterparty, OffsetDateTime transactionDate) {

    /** Both sides of an intra-bank transfer. */
    public record TransferLegs(String transferId, String fromAccountNumber, String fromTransactionId, String toAccountNumber,
            String toTransactionId) {
    }

    /** The other side: for a credit, the sender; for a debit, the recipient. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Counterparty(String accountName, String accountNumber, String institutionCode, String bankName) {
    }
}
