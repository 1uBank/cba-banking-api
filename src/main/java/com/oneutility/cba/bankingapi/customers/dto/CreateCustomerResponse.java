package com.oneutility.cba.bankingapi.customers.dto;

import com.oneutility.cba.bankingapi.corebanking.dto.CustomerType;
import java.time.LocalDate;

/** The new customer and their first account. {@code id} is the account's ID in CBA-CORE. */
public record CreateCustomerResponse(String accountNumber, long id, long clientId, CustomerType clientType,
        String firstName, String lastName, String businessName, String email, Address address, LocalDate dateOfBirth,
        LocalDate registrationDate, String mobileNumber, long productId, boolean subLedgerActivated) {
}
