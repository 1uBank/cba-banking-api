package com.oneutility.cba.bankingapi.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The token response. OAuth 2.0 fixes these snake_case names; the rest of the API is camelCase. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenResponse(@JsonProperty("access_token") String accessToken, @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn, @JsonProperty("scope") String scope) {

    public static TokenResponse from(IssuedToken token) {
        return new TokenResponse(token.accessToken(), "Bearer", token.expiresInSeconds(), token.scope());
    }
}
