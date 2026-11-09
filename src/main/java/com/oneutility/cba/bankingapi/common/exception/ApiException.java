package com.oneutility.cba.bankingapi.common.exception;

import java.util.List;

/**
 * Thrown by any module to return a contract error. The handler turns it into an RFC 9457 problem details response
 * with the code's status and title.
 *
 * <pre>{@code throw new ApiException(ErrorCode.ACCOUNT_NOT_FOUND, "No account 2000000158 for this client");}</pre>
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient List<FieldViolation> violations;

    public ApiException(ErrorCode code, String detail) {
        this(code, detail, List.of());
    }

    public ApiException(ErrorCode code, String detail, List<FieldViolation> violations) {
        super(detail);
        this.code = code;
        this.violations = List.copyOf(violations);
    }

    public ErrorCode code() {
        return code;
    }

    public List<FieldViolation> violations() {
        return violations;
    }
}
