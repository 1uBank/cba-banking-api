package com.oneutility.cba.bankingapi.customers.dto;

import com.oneutility.cba.bankingapi.corebanking.dto.CustomerType;
import com.oneutility.cba.bankingapi.corebanking.dto.RiskRating;
import java.time.LocalDate;
import java.util.List;

/**
 * A customer, as the contract returns them. Names and date of birth are for individuals; business name and
 * registration date for businesses. {@code transactionLimit} is a two-decimal string.
 */
public record Customer(long clientId, CustomerType clientType, String firstName, String middleName,
        String lastName, String businessName, String email, String mobileNumber, LocalDate dateOfBirth,
        LocalDate registrationDate, String bvn, String nin, String cacNumber, String tin, Address address,
        int kycLevel, Boolean pep, RiskRating riskRating, String transactionLimit, List<RelatedPerson> relatedPersons,
        String status) {
}
