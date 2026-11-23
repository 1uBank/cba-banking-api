package com.oneutility.cba.bankingapi.customers.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.List;

/**
 * {@code PUT /v1/business-customers/{clientId}}: only the fields that change. {@code address} replaces the address
 * as a whole; {@code relatedPersons}, when sent, replaces the whole list.
 */
public record UpdateBusinessRequest(@Pattern(regexp = ".*\\S.*", message = "must not be blank") String businessName,
        LocalDate registrationDate, @Email @Pattern(regexp = ".*\\S.*", message = "must not be blank") String email,
        @Pattern(regexp = ".*\\S.*", message = "must not be blank") String mobileNumber,
        @Pattern(regexp = Patterns.BVN, message = "must be exactly 11 digits") String bvn,
        @Pattern(regexp = Patterns.CAC_NUMBER, message = "must be 1 to 20 letters, digits, / or -") String cacNumber,
        @Pattern(regexp = Patterns.TIN, message = "must be 8 to 20 digits or -, starting with a digit") String tin,
        @Valid Address address, List<@Valid @NotNull RelatedPerson> relatedPersons) {

    @JsonIgnore
    public boolean isEmpty() {
        return businessName == null && registrationDate == null && email == null && mobileNumber == null && bvn == null
                && cacNumber == null && tin == null && address == null && relatedPersons == null;
    }
}
