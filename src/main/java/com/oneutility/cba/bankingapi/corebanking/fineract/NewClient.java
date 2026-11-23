package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Fineract's {@code POST /clients} body: the client, their address and datatable rows, and the first savings account
 * ({@code savingsProductId}), opened and activated with the client. Dates are {@code yyyy-MM-dd}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NewClient(long officeId, int legalFormId, String firstname, String middlename, String lastname,
        String fullname, String dateOfBirth, String emailAddress, String mobileNo, boolean active,
        String activationDate, String submittedOnDate, long savingsProductId, List<AddressFields> address,
        List<DatatableEntry> datatables, String locale, String dateFormat) {
}
