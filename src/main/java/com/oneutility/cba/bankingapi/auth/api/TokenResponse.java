package com.oneutility.cba.bankingapi.auth.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.oneutility.cba.bankingapi.auth.application.IssuedToken;

/** The token response. OAuth 2.0 fixes these snake_case names; the rest of the API is camelCase. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record TokenResponse(@JsonProperty("access_token") String accessToken, @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn, @JsonProperty("scope") String scope) {

    static TokenResponse from(IssuedToken token) {
        return new TokenResponse(token.accessToken(), "Bearer", token.expiresInSeconds(), token.scope());
    }
}
