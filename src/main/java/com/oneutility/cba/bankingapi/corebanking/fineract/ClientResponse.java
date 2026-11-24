package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.LocalDate;

/** Fineract's {@code GET /clients/{id}}, with the fields the Banking API uses. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientResponse(long id, Long officeId, String firstname, String middlename, String lastname, String fullname,
        @JsonDeserialize(using = FineractDates.class) LocalDate dateOfBirth, String emailAddress, String mobileNo,
        EnumOption legalForm, EnumOption status) {
}
