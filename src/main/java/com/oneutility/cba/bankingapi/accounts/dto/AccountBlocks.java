package com.oneutility.cba.bankingapi.accounts.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * What is blocked on an account ({@code NONE}, {@code CREDIT}, {@code DEBIT} or {@code ALL}) and each blocked side,
 * with who placed it. The reason is given only for blocks the client placed.
 */
public record AccountBlocks(String accountNumber, String block, List<Side> blocks) {

    public AccountBlocks {
        blocks = List.copyOf(blocks);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Side(String type, String placedBy, String reason, OffsetDateTime placedDate) {
    }
}
