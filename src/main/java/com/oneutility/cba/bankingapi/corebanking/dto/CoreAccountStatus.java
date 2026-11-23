package com.oneutility.cba.bankingapi.corebanking.dto;

/**
 * A savings account's state as the Banking API reports it. {@code PENDING} covers accounts not yet active
 * (submitted, approved, rejected or withdrawn), which customers cannot use.
 */
public enum CoreAccountStatus {
    ACTIVE, DORMANT, RESTRICTED, CLOSED, PENDING
}
