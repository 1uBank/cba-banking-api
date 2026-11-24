package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;

/**
 * Fineract's {@code POST /savingsaccounts/{id}/transactions?command=holdAmount}: holds an amount on the account. The
 * client's reference is the hold transaction's {@code externalId} (unique across all transactions).
 */
public record HoldAmountCommand(@JsonSerialize(using = PlainDecimal.class) BigDecimal transactionAmount,
        String transactionDate, String reasonForBlock, String externalId, String locale, String dateFormat) {

    public static HoldAmountCommand of(BigDecimal amount, String date, String reason, String reference) {
        return new HoldAmountCommand(amount, date, reason, reference, FineractLocale.ENGLISH, FineractLocale.DATE_FORMAT);
    }
}
