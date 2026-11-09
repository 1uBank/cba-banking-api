package com.oneutility.cba.bankingapi.auth.application;

/** Asks the auth service for a client-credentials token. */
public interface AuthServiceTokenGateway {

    /**
     * @param scope the scopes asked for, or {@code null} for all the client's scopes
     * @throws TokenRequestException when the auth service refuses the request or cannot be reached
     */
    IssuedToken requestToken(ClientCredentials credentials, String scope);
}
