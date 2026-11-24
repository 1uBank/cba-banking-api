package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** A page of virtual accounts, oldest first, and whether more follow. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VirtualAccountListResponse(List<VirtualAccountResponse> items, boolean hasMore) {

    public VirtualAccountListResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
