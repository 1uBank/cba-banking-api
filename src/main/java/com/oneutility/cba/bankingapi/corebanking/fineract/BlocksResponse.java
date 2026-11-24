package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;

/** 1UBank's {@code GET /oneubank/blocks}: each side of an account's block, with who placed it, why and when (UTC). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BlocksResponse(long savingsId, Side debit, Side credit) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Side(boolean blocked, String placedBy, String reason, OffsetDateTime placedOn) {
    }
}
