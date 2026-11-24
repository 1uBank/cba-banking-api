package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.math.BigDecimal;
import java.time.LocalDate;

/** One of Fineract's savings transactions ({@code GET /savingsaccounts/{id}/transactions/...}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SavingsTransactionResponse(long id, String accountNo, String externalId,
        @JsonDeserialize(using = FineractDates.class) LocalDate date, BigDecimal amount, boolean reversed, String note,
        PaymentDetail paymentDetailData) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentDetail(CodeValueRef paymentType) {
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public String paymentTypeName() {
        return paymentDetailData == null || paymentDetailData.paymentType() == null ? null : paymentDetailData.paymentType().name();
    }
}
