package com.oneutility.cba.bankingapi.virtualaccounts.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** A page of virtual accounts; {@code nextCursor} is absent on the last page. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VirtualAccountPage(List<VirtualAccount> items, String nextCursor) {

    public VirtualAccountPage {
        items = List.copyOf(items);
    }
}
