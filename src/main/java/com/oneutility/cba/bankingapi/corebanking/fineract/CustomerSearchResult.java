package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** The answer to a customer search: matching client IDs, in order, and whether more follow. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomerSearchResult(List<Long> clientIds, boolean hasMore) {

    public CustomerSearchResult {
        clientIds = clientIds == null ? List.of() : List.copyOf(clientIds);
    }
}
