package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonInclude;

/** An address in a client create or 1UBank customer update request; IDs are code values (type, state, country). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AddressFields(long addressTypeId, String addressLine1, String addressLine2, String addressLine3,
        String street, String townVillage, String city, String countyDistrict, Long stateProvinceId, long countryId,
        String postalCode, boolean isActive) {
}
