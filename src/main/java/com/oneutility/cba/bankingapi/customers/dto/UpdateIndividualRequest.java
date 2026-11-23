package com.oneutility.cba.bankingapi.customers.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/**
 * {@code PUT /v1/individual-customers/{clientId}}: only the fields that change. {@code address} replaces the address
 * as a whole.
 */
public record UpdateIndividualRequest(@Pattern(regexp = ".*\\S.*", message = "must not be blank") String firstName,
        String middleName, @Pattern(regexp = ".*\\S.*", message = "must not be blank") String lastName,
        LocalDate dateOfBirth, @Email @Pattern(regexp = ".*\\S.*", message = "must not be blank") String email,
        @Pattern(regexp = ".*\\S.*", message = "must not be blank") String mobileNumber,
        @Pattern(regexp = Patterns.BVN, message = "must be exactly 11 digits") String bvn,
        @Pattern(regexp = Patterns.NIN, message = "must be exactly 11 digits") String nin,
        @Pattern(regexp = Patterns.TIN, message = "must be 8 to 20 digits or -, starting with a digit") String tin,
        @Valid Address address) {

    @JsonIgnore
    public boolean isEmpty() {
        return firstName == null && middleName == null && lastName == null && dateOfBirth == null && email == null
                && mobileNumber == null && bvn == null && nin == null && tin == null && address == null;
    }
}
