package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;

/** {@code Customer Limits}: the customer's own daily transaction limit. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LimitRow(@JsonProperty("Transaction Limit") @JsonSerialize(using = PlainDecimal.class) BigDecimal transactionLimit,
        String locale) implements DatatableRow {

    public static final String TABLE = "Customer Limits";

    public static LimitRow of(BigDecimal transactionLimit) {
        return new LimitRow(transactionLimit, FineractLocale.ENGLISH);
    }
}
