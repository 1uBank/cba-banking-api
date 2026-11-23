package com.oneutility.cba.bankingapi.corebanking.dto;

/** Finds an owner's customers matching every non-null field, after {@code afterClientId}, in client ID order. */
public record CustomerSearch(String owner, String bvn, String nin, String email, String mobileNumber,
        Long afterClientId, int limit) {
}
