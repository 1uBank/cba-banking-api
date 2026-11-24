package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The body of Fineract's savings account commands that take at most a reason: {@code block}, {@code blockDebit},
 * {@code blockCredit} (reason required), the matching unblocks and {@code releaseAmount} (no fields).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccountCommand(String reasonForBlock) {

    public static AccountCommand withReason(String reason) {
        return new AccountCommand(reason);
    }

    public static AccountCommand empty() {
        return new AccountCommand(null);
    }
}
