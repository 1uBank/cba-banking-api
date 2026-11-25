package com.oneutility.cba.bankingapi.corebanking.dto;

import java.util.List;

/** A page of transactions, newest first. */
public record CoreTransactionPage(List<CoreTransaction> items, boolean hasMore) {

    public CoreTransactionPage {
        items = List.copyOf(items);
    }
}
