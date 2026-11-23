package com.oneutility.cba.bankingapi.corebanking.dto;

/** A customer's address in CBA-CORE. {@code state} and {@code country} are names from CBA-CORE's lists. */
public record CoreAddress(String addressLine1, String addressLine2, String addressLine3, String street,
        String townVillage, String city, String district, String state, String country, String postalCode) {
}
