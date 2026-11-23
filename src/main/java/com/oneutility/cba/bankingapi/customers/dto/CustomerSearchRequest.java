package com.oneutility.cba.bankingapi.customers.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;

/** {@code POST /v1/customers/search}: at least one field; every field sent must match. */
public record CustomerSearchRequest(@Pattern(regexp = Patterns.BVN, message = "must be exactly 11 digits") String bvn, @Email String email,
        String mobileNumber) {
}
