package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 1UBank's {@code POST /oneubank/virtual-accounts}: a static virtual account under the parent, on the product set up
 * for its type. {@code accountNumber} is for {@code AIRTIME} only.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateVirtualAccountCommand(String parentAccountNumber, String type, String label, String accountNumber,
        String transactionReference) {
}
