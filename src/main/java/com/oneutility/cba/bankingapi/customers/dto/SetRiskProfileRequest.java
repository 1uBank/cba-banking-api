package com.oneutility.cba.bankingapi.customers.dto;

import com.oneutility.cba.bankingapi.corebanking.dto.RiskRating;

/** {@code PUT /v1/customers/{clientId}/risk-profile}: either or both; a field left out keeps its value. */
public record SetRiskProfileRequest(Boolean pep, RiskRating riskRating) {
}
