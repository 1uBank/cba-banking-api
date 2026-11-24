package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;

/**
 * Fineract's {@code POST /accounttransfers} between two savings accounts (account type {@code 2}). The client's
 * reference goes with it as the {@code Idempotency-Key}.
 */
public record AccountTransferCommand(long fromOfficeId, long fromClientId, int fromAccountType, long fromAccountId,
        long toOfficeId, long toClientId, int toAccountType, long toAccountId,
        @JsonSerialize(using = PlainDecimal.class) BigDecimal transferAmount, String transferDate, String transferDescription,
        String locale, String dateFormat) {

    static final int SAVINGS = 2;

    public static AccountTransferCommand savingsToSavings(long fromOfficeId, long fromClientId, long fromSavingsId, long toOfficeId,
            long toClientId, long toSavingsId, BigDecimal amount, String date, String description) {
        return new AccountTransferCommand(fromOfficeId, fromClientId, SAVINGS, fromSavingsId, toOfficeId, toClientId, SAVINGS,
                toSavingsId, amount, date, description, FineractLocale.ENGLISH, FineractLocale.DATE_FORMAT);
    }
}
