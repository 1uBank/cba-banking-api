package com.oneutility.cba.bankingapi.customers.dto;

import com.oneutility.cba.bankingapi.corebanking.dto.RiskRating;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

/** {@code POST /v1/individual-customers}: a person and their first account. */
public record CreateIndividualRequest(@NotBlank String firstName, String middleName, @NotBlank String lastName,
        LocalDate dateOfBirth, @NotBlank @Email String email, @NotBlank String mobileNumber,
        @NotNull @Pattern(regexp = Patterns.BVN, message = "must be exactly 11 digits") String bvn,
        @Pattern(regexp = Patterns.NIN, message = "must be exactly 11 digits") String nin,
        @Pattern(regexp = Patterns.TIN, message = "must be 8 to 20 digits or -, starting with a digit") String tin,
        @NotNull @Valid Address address, @NotNull @Positive Long productId, @NotNull @Min(1) Integer kycLevel,
        @Pattern(regexp = Patterns.AMOUNT, message = "must be an amount above zero with at most 2 decimal places") String transactionLimit,
        Boolean pep, RiskRating riskRating,
        @Pattern(regexp = Patterns.TRANSACTION_REFERENCE, message = "must be 1 to 50 letters, digits, _ or -") String transactionReference) {
}
