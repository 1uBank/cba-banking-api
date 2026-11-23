package com.oneutility.cba.bankingapi.corebanking.dto;

import java.util.List;

/** One page of matching customers' IDs; {@code hasMore} when another page follows. */
public record CustomerIds(List<Long> clientIds, boolean hasMore) {

    public CustomerIds {
        clientIds = List.copyOf(clientIds);
    }
}
