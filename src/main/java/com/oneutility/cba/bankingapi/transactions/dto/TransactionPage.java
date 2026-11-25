package com.oneutility.cba.bankingapi.transactions.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** A page of transactions, newest first; {@code nextCursor} is absent on the last page. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionPage(List<Transaction> items, String nextCursor) {

    public TransactionPage {
        items = List.copyOf(items);
    }
}
