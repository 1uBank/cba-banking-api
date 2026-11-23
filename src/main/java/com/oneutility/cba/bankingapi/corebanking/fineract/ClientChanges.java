package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Fineract's {@code PUT /clients/{id}} body: only the fields that change. Dates are {@code yyyy-MM-dd}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClientChanges(String firstname, String middlename, String lastname, String fullname, String dateOfBirth,
        String emailAddress, String mobileNo, String locale, String dateFormat) {

    /** Nothing to change; not sent. */
    @JsonIgnore
    public boolean isEmpty() {
        return firstname == null && middlename == null && lastname == null && fullname == null && dateOfBirth == null
                && emailAddress == null && mobileNo == null;
    }
}
