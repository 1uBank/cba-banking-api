package com.oneutility.cba.bankingapi.auth.dto;

/** A client's ID and secret. {@link #toString()} never shows the secret. */
public record ClientCredentials(String clientId, String clientSecret) {

    @Override
    public String toString() {
        return "ClientCredentials[clientId=" + clientId + "]";
    }
}
