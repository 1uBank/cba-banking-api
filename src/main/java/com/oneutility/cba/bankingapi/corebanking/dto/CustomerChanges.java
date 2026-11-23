package com.oneutility.cba.bankingapi.corebanking.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Changes to a customer. {@code null} leaves a field as it is; {@code address} replaces the address as a whole and
 * {@code relatedPersons} the whole list.
 */
public record CustomerChanges(String firstName, String middleName, String lastName, String fullName,
        LocalDate dateOfBirth, String email, String mobileNumber, String bvn, String nin, String cacNumber,
        String tin, CoreAddress address, List<CoreRelatedPerson> relatedPersons) {

    public CustomerChanges {
        relatedPersons = relatedPersons == null ? null : List.copyOf(relatedPersons);
    }
}
