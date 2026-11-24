package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;

/**
 * Fineract's {@code POST /savingsaccounts/{id}/transactions?command=withdrawal}. The payment type decides the ledger
 * entry; the client's reference is the transaction's {@code externalId} (unique across all transactions).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WithdrawalCommand(String transactionDate, @JsonSerialize(using = PlainDecimal.class) BigDecimal transactionAmount,
        long paymentTypeId, String externalId, String note, String locale, String dateFormat) {

    public static WithdrawalCommand of(String date, BigDecimal amount, long paymentTypeId, String reference, String note) {
        return new WithdrawalCommand(date, amount, paymentTypeId, reference, note, FineractLocale.ENGLISH, FineractLocale.DATE_FORMAT);
    }
}
