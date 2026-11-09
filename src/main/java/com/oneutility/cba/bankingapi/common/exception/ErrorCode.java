package com.oneutility.cba.bankingapi.common.exception;

import org.springframework.http.HttpStatus;

/**
 * The stable error codes of the API contract ({@code Error.code} in {@code api/banking-api.yaml}). Clients branch on
 * these, so a code is never renamed or removed; add new ones to the contract first.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request is not valid"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Not allowed"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "Account not found"),
    TRANSFER_NOT_FOUND(HttpStatus.NOT_FOUND, "Transfer not found"),
    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "Customer not found"),
    CUSTOMER_EXISTS(HttpStatus.CONFLICT, "Customer already exists"),
    ACCOUNT_NUMBER_TAKEN(HttpStatus.CONFLICT, "Account number already in use"),
    GROUP_CODE_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "Group code not allowed"),
    INVOICE_NOT_FOUND(HttpStatus.NOT_FOUND, "Invoice not found"),
    HOLD_NOT_FOUND(HttpStatus.NOT_FOUND, "Hold not found"),
    PAYMENT_TYPE_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "Payment type not allowed"),
    TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Transaction not found"),
    REFERENCE_REUSED(HttpStatus.CONFLICT, "Reference already used"),
    REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "Request in progress"),
    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds"),
    LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Limit exceeded"),
    ACCOUNT_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_ENTITY, "Account not active"),
    ACCOUNT_RESTRICTED(HttpStatus.UNPROCESSABLE_ENTITY, "Account restricted"),
    SCREENING_REFUSED(HttpStatus.UNPROCESSABLE_ENTITY, "Refused by screening"),
    UPSTREAM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable"),
    UPSTREAM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "Service timed out"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    /** The last segment of the problem {@code type} URI, for example {@code reference-reused}. */
    public String slug() {
        return name().toLowerCase().replace('_', '-');
    }
}
