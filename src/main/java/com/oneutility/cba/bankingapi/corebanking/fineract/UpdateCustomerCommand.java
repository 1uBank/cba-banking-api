package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 1UBank's {@code PUT /oneubank/customers/{clientId}}: any of the client's fields, identifiers to add or replace, the
 * address (replaced as a whole) and datatables whose rows are replaced, in one transaction.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateCustomerCommand(ClientChanges client, List<IdentifierFields> identifiers, AddressFields address,
        List<DatatableRows> datatableRows) {

    /** Nothing to change; not sent. */
    @JsonIgnore
    public boolean isEmpty() {
        return (client == null || client.isEmpty()) && (identifiers == null || identifiers.isEmpty()) && address == null
                && (datatableRows == null || datatableRows.isEmpty());
    }
}
