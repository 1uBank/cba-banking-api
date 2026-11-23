package com.oneutility.cba.bankingapi.corebanking.fineract;

import java.util.List;

/** 1UBank's {@code POST /oneubank/customers}: the client and their identifiers, in one transaction. */
public record CreateCustomerCommand(NewClient client, List<IdentifierFields> identifiers) {

    public CreateCustomerCommand {
        identifiers = List.copyOf(identifiers);
    }
}
