package com.oneutility.cba.bankingapi.auth.service;

import org.springframework.http.HttpStatus;

/**
 * A token request refused, in OAuth 2.0 terms (RFC 6749, section 5.2): an {@code error} code such as
 * {@code invalid_client}, a description, and the HTTP status to answer with.
 */
public class TokenRequestException extends RuntimeException {

    private final HttpStatus status;
    private final String error;

    public TokenRequestException(HttpStatus status, String error, String description) {
        super(description);
        this.status = status;
        this.error = error;
    }

    public static TokenRequestException invalidClient() {
        return new TokenRequestException(HttpStatus.UNAUTHORIZED, "invalid_client",
                "Invalid client or Invalid client credentials");
    }

    public static TokenRequestException unavailable() {
        return new TokenRequestException(HttpStatus.SERVICE_UNAVAILABLE, "temporarily_unavailable",
                "Tokens cannot be issued right now; retry shortly");
    }

    public HttpStatus status() {
        return status;
    }

    public String error() {
        return error;
    }
}
