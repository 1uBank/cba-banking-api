package com.oneutility.cba.bankingapi.customers.dto;

import com.oneutility.cba.bankingapi.corebanking.dto.RelatedPersonRole;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** A director or signatory of a business customer. */
public record RelatedPerson(@NotBlank @Size(max = 100) String firstName, @Size(max = 100) String middleName,
        @NotBlank @Size(max = 100) String lastName, @NotNull RelatedPersonRole role,
        @Pattern(regexp = Patterns.BVN, message = "must be exactly 11 digits") String bvn, @Pattern(regexp = Patterns.NIN, message = "must be exactly 11 digits") String nin, LocalDate dateOfBirth,
        @Size(max = 20) String mobileNumber, @Email @Size(max = 100) String email, @Size(max = 250) String address,
        @DecimalMin("0") @DecimalMax("100") BigDecimal ownershipPercentage) {
}
