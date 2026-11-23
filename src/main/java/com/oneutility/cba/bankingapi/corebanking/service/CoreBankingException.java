package com.oneutility.cba.bankingapi.corebanking.service;

import java.util.List;

/**
 * CBA-CORE refused a call, or could not be reached. Services turn it into the contract's errors.
 *
 * @see #hasCode(String)
 */
public class CoreBankingException extends RuntimeException {

    /** Why the call failed. */
    public enum Kind {
        /** CBA-CORE answered with an error status. */
        REFUSED,
        /** No answer: connection refused, reset or similar. */
        UNAVAILABLE,
        /** No answer in time; the call may or may not have taken effect. */
        TIMEOUT
    }

    private final Kind kind;
    private final int status;
    private final transient List<String> codes;

    CoreBankingException(Kind kind, int status, List<String> codes, String message) {
        super(message);
        this.kind = kind;
        this.status = status;
        this.codes = List.copyOf(codes);
    }

    static CoreBankingException unavailable(String message) {
        return new CoreBankingException(Kind.UNAVAILABLE, 0, List.of(), message);
    }

    static CoreBankingException timeout(String message) {
        return new CoreBankingException(Kind.TIMEOUT, 0, List.of(), message);
    }

    public Kind kind() {
        return kind;
    }

    /** CBA-CORE's HTTP status, or 0 when it did not answer. */
    public int status() {
        return status;
    }

    /** CBA-CORE's error codes ({@code userMessageGlobalisationCode}), the overall one first. */
    public List<String> codes() {
        return codes;
    }

    /** Whether CBA-CORE gave this error code, overall or for a field. */
    public boolean hasCode(String code) {
        return codes.contains(code);
    }

    public boolean isNotFound() {
        return kind == Kind.REFUSED && status == 404;
    }
}
