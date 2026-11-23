package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** {@code Customer KYC}: the KYC level, a value of code list {@code KycLevel}. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KycRow(@JsonProperty("KycLevel_cd_KYC Level") Long kycLevelId, String locale) implements DatatableRow {

    public static final String TABLE = "Customer KYC";

    public static KycRow of(long kycLevelId) {
        return new KycRow(kycLevelId, FineractLocale.ENGLISH);
    }
}
