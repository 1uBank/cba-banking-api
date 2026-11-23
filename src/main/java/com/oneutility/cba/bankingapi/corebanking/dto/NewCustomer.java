package com.oneutility.cba.bankingapi.corebanking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A customer to create with their first account, in one step. Optional fields are {@code null}.
 *
 * @param owner the API client the customer belongs to
 * @param idempotencyKey makes a resend return the first result instead of creating a second customer; optional
 */
public record NewCustomer(String owner, CustomerType type, String firstName, String middleName, String lastName,
        String fullName, LocalDate dateOfBirth, String email, String mobileNumber, String bvn, String nin,
        String cacNumber, String tin, CoreAddress address, long productId, int kycLevel, Boolean pep, RiskRating riskRating,
        BigDecimal transactionLimit, List<CoreRelatedPerson> relatedPersons, String idempotencyKey) {

    public NewCustomer {
        relatedPersons = relatedPersons == null ? List.of() : List.copyOf(relatedPersons);
    }
}
