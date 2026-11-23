package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** {@code Customer Owner}: the API client a customer belongs to. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OwnerRow(@JsonProperty("Owner") String owner) implements DatatableRow {

    public static final String TABLE = "Customer Owner";
}
