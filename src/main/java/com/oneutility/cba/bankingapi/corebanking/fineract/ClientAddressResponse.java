package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One of Fineract's {@code GET /client/{id}/addresses}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientAddressResponse(long addressId, Long addressTypeId, Boolean isActive, String addressLine1,
        String addressLine2, String addressLine3, String street, String townVillage, String city, String countyDistrict,
        String stateName, String countryName, String postalCode) {
}
