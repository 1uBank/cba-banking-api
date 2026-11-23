package com.oneutility.cba.bankingapi.customers.dto;

import com.oneutility.cba.bankingapi.corebanking.dto.RiskRating;

/** A customer's PEP status and risk rating. */
public record RiskProfile(long clientId, Boolean pep, RiskRating riskRating) {
}
