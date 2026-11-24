package com.oneutility.cba.bankingapi.accounts.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What is blocked on an account: {@code NONE}, {@code CREDIT}, {@code DEBIT} or {@code ALL}, with the reason given
 * when it was last blocked.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccountBlocks(String accountNumber, String block, String reason) {
}
