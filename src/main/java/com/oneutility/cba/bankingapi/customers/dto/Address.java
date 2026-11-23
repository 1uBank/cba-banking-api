package com.oneutility.cba.bankingapi.customers.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A customer's address, as in the contract. {@code state} and {@code country} are names. */
public record Address(@NotBlank @Size(max = 500) String addressLine1, @Size(max = 500) String addressLine2,
        @Size(max = 500) String addressLine3, @Size(max = 100) String street, @Size(max = 100) String townVillage,
        @Size(max = 100) String city, @Size(max = 100) String district, String state, String country,
        @Size(max = 10) String postalCode) {
}
