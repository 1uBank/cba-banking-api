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
import java.util.List;

/** {@code POST /v1/business-customers}: a business, its directors and signatories, and its first account. */
public record CreateBusinessRequest(@NotBlank String businessName, LocalDate registrationDate,
        @NotBlank @Email String email, @NotBlank String mobileNumber,
        @NotNull @Pattern(regexp = Patterns.BVN, message = "must be exactly 11 digits") String bvn,
        @Pattern(regexp = Patterns.CAC_NUMBER, message = "must be 1 to 20 letters, digits, / or -") String cacNumber,
        @Pattern(regexp = Patterns.TIN, message = "must be 8 to 20 digits or -, starting with a digit") String tin,
        @NotNull @Valid Address address, @NotNull @Positive Long productId, @NotNull @Min(1) Integer kycLevel,
        List<@Valid @NotNull RelatedPerson> relatedPersons,
        @Pattern(regexp = Patterns.AMOUNT, message = "must be an amount above zero with at most 2 decimal places") String transactionLimit,
        Boolean pep, RiskRating riskRating,
        @Pattern(regexp = Patterns.TRANSACTION_REFERENCE, message = "must be 1 to 50 letters, digits, _ or -") String transactionReference) {
}
