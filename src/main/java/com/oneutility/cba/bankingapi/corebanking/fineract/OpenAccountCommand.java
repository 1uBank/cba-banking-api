package com.oneutility.cba.bankingapi.corebanking.fineract;

/** 1UBank's {@code POST /oneubank/customers/{clientId}/accounts}: open and activate an account on the product. */
public record OpenAccountCommand(long productId) {
}
