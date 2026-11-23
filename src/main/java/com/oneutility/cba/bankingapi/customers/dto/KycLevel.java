package com.oneutility.cba.bankingapi.customers.dto;

/** A customer's KYC level. */
public record KycLevel(long clientId, int kycLevel) {
}
