package com.oneutility.cba.bankingapi.accounts.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** A page of accounts; {@code nextCursor} is absent on the last page. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccountPage(List<Account> items, String nextCursor) {

    public AccountPage {
        items = List.copyOf(items);
    }
}
