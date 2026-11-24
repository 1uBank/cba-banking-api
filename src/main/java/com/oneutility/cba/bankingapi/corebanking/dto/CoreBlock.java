package com.oneutility.cba.bankingapi.corebanking.dto;

/** What is blocked on an account: nothing, payments in, payments out, or both. */
public enum CoreBlock {
    NONE, CREDIT, DEBIT, ALL;

    public boolean credit() {
        return this == CREDIT || this == ALL;
    }

    public boolean debit() {
        return this == DEBIT || this == ALL;
    }

    public static CoreBlock of(boolean debit, boolean credit) {
        return debit && credit ? ALL : debit ? DEBIT : credit ? CREDIT : NONE;
    }
}
