package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 1UBank's {@code POST /oneubank/customers/search}: the owner's customers matching every criterion sent. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomerSearchQuery(String owner, String bvn, String nin, String email, String mobileNumber,
        Long afterClientId, int limit) {
}
