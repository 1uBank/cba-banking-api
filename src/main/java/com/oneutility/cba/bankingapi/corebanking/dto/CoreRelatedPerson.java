package com.oneutility.cba.bankingapi.corebanking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A director or signatory of a business customer. */
public record CoreRelatedPerson(String firstName, String middleName, String lastName, RelatedPersonRole role,
        String bvn, String nin, LocalDate dateOfBirth, String mobileNumber, String email, String address,
        BigDecimal ownershipPercentage) {
}
