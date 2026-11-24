package com.oneutility.cba.bankingapi.corebanking.dto;

import java.util.List;

/** A page of virtual accounts, oldest first. */
public record CoreVirtualAccountPage(List<CoreVirtualAccount> items, boolean hasMore) {

    public CoreVirtualAccountPage {
        items = List.copyOf(items);
    }
}
