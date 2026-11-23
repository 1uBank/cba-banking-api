package com.oneutility.cba.bankingapi.corebanking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A customer as CBA-CORE holds them. Optional fields are {@code null} when not recorded.
 *
 * @param owner the API client the customer belongs to, or {@code null} if none is recorded
 * @param status {@code ACTIVE}, {@code INACTIVE} or {@code CLOSED}
 */
public record CoreCustomer(long clientId, String owner, CustomerType type, String firstName, String middleName,
        String lastName, String fullName, LocalDate dateOfBirth, String email, String mobileNumber, String bvn,
        String nin, String cacNumber, String tin, CoreAddress address, Integer kycLevel, Boolean pep, RiskRating riskRating,
        BigDecimal transactionLimit, List<CoreRelatedPerson> relatedPersons, String status) {

    public CoreCustomer {
        relatedPersons = relatedPersons == null ? List.of() : List.copyOf(relatedPersons);
    }
}
