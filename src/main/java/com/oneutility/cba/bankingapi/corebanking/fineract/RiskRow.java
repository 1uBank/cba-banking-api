package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** {@code Customer Risk}: PEP and the risk rating (code list {@code RiskRating}). A {@code null} is not sent. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RiskRow(@JsonProperty("PEP") Boolean pep, @JsonProperty("RiskRating_cd_Risk Rating") Long riskRatingId,
        String locale) implements DatatableRow {

    public static final String TABLE = "Customer Risk";

    public static RiskRow of(Boolean pep, Long riskRatingId) {
        return new RiskRow(pep, riskRatingId, FineractLocale.ENGLISH);
    }
}
