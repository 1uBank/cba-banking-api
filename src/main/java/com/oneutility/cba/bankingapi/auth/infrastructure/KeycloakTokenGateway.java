package com.oneutility.cba.bankingapi.auth.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oneutility.cba.bankingapi.auth.application.AuthServiceTokenGateway;
import com.oneutility.cba.bankingapi.auth.application.ClientCredentials;
import com.oneutility.cba.bankingapi.auth.application.IssuedToken;
import com.oneutility.cba.bankingapi.auth.application.TokenRequestException;
import com.oneutility.cba.bankingapi.shared.security.AuthServiceProperties;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Forwards token requests to the auth service's {@code clients} realm (Keycloak's OpenID Connect token endpoint). */
@Component
class KeycloakTokenGateway implements AuthServiceTokenGateway {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakTokenGateway.class);

    private final RestClient http;

    KeycloakTokenGateway(RestClient.Builder builder, AuthServiceProperties authService) {
        ClientHttpRequestFactorySettings timeouts = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(authService.connectTimeout()).withReadTimeout(authService.readTimeout());
        this.http = builder.baseUrl(authService.tokenUrl().toString())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(timeouts)).build();
    }

    @Override
    public IssuedToken requestToken(ClientCredentials credentials, String scope) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        if (scope != null) {
            form.add("scope", scope);
        }
        try {
            KeycloakToken token = http.post().contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBasicAuth(credentials.clientId(), credentials.clientSecret(),
                            StandardCharsets.UTF_8))
                    .body(form).retrieve().body(KeycloakToken.class);
            if (token == null || token.accessToken() == null) {
                LOG.error("The auth service returned no access token for client {}", credentials.clientId());
                throw TokenRequestException.unavailable();
            }
            return new IssuedToken(token.accessToken(), token.expiresIn(), token.scope());
        } catch (HttpClientErrorException e) {
            throw refused(e, credentials.clientId());
        } catch (RestClientException e) {
            LOG.error("Could not get a token from the auth service for client {}: {}", credentials.clientId(),
                    e.getMessage());
            throw TokenRequestException.unavailable();
        }
    }

    private static TokenRequestException refused(HttpClientErrorException e, String clientId) {
        KeycloakError error = readError(e);
        HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
        LOG.warn("The auth service refused a token for client {}: {} {}", clientId, status.value(),
                error == null ? "" : error.error());
        if (status == HttpStatus.UNAUTHORIZED || error == null || error.error() == null
                || "invalid_client".equals(error.error()) || "unauthorized_client".equals(error.error())) {
            // Never tell a caller whether the ID or the secret was wrong.
            return TokenRequestException.invalidClient();
        }
        return new TokenRequestException(HttpStatus.BAD_REQUEST, error.error(), error.errorDescription());
    }

    private static KeycloakError readError(HttpClientErrorException e) {
        try {
            return e.getResponseBodyAs(KeycloakError.class);
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    record KeycloakToken(@JsonProperty("access_token") String accessToken, @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("scope") String scope) {
    }

    record KeycloakError(@JsonProperty("error") String error,
            @JsonProperty("error_description") String errorDescription) {
    }
}
