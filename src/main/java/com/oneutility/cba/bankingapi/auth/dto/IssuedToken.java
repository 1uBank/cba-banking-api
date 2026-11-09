package com.oneutility.cba.bankingapi.auth.dto;

/** An access token issued by the auth service. */
public record IssuedToken(String accessToken, long expiresInSeconds, String scope) {

    @Override
    public String toString() {
        return "IssuedToken[expiresInSeconds=" + expiresInSeconds + ", scope=" + scope + "]";
    }
}
