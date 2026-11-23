package com.oneutility.cba.bankingapi.customers.dto;

/** The contract's formats for identity numbers, amounts and references. */
public final class Patterns {

    public static final String BVN = "^\\d{11}$";
    public static final String NIN = "^\\d{11}$";
    public static final String CAC_NUMBER = "^[A-Za-z0-9/-]{1,20}$";
    public static final String TIN = "^[0-9][0-9-]{7,19}$";
    public static final String AMOUNT = "^(?=.*[1-9])\\d{1,15}(\\.\\d{1,2})?$";
    public static final String TRANSACTION_REFERENCE = "^[A-Za-z0-9_-]{1,50}$";

    private Patterns() {
    }
}
