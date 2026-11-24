package com.oneutility.cba.bankingapi.virtualaccounts.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

/** {@code VirtualAccount}. {@code name} is what payers see: {@code <owner's name> - <label>}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VirtualAccount(String accountNumber, String name, String label, String parentAccountNumber,
        VirtualAccountType type, String status, OffsetDateTime createdDate, OffsetDateTime updatedDate) {
}
