package com.oneutility.cba.bankingapi.corebanking.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The Banking API's own token for CBA-CORE, from the auth service's {@code cba} realm (client credentials). Kept until
 * shortly before it expires, and dropped when CBA-CORE turns it down.
 */
class CoreBankingTokens {

    /** Renew this long before expiry, so a token never expires on its way to CBA-CORE. */
    static final Duration RENEW_BEFORE_EXPIRY = Duration.ofSeconds(30);

    private final RestClient http;
    private final CoreBankingProperties properties;
    private final Clock clock;

    private String token;
    private Instant renewAt = Instant.MIN;

    CoreBankingTokens(RestClient http, CoreBankingProperties properties, Clock clock) {
        this.http = http;
        this.properties = properties;
        this.clock = clock;
    }

    /** A valid access token, fetched if the one held is missing or about to expire. */
    synchronized String current() {
        if (token == null || !clock.instant().isBefore(renewAt)) {
            fetch();
        }
        return token;
    }

    /** Forgets the token held, for example after CBA-CORE answered 401. */
    synchronized void invalidate(String rejected) {
        if (rejected != null && rejected.equals(token)) {
            token = null;
        }
    }

    private void fetch() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        Instant requestedAt = clock.instant();
        TokenResponse response;
        try {
            response = http.post().uri(properties.tokenUrl()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(h -> h.setBasicAuth(properties.clientId(), properties.clientSecret(), StandardCharsets.UTF_8))
                    .body(form).retrieve().body(TokenResponse.class);
        } catch (RestClientException e) {
            throw CoreBankingException.unavailable("No token from the auth service for CBA-CORE: " + e.getMessage());
        }
        if (response == null || response.accessToken() == null) {
            throw CoreBankingException.unavailable("The auth service returned no token for CBA-CORE");
        }
        token = response.accessToken();
        renewAt = requestedAt.plusSeconds(Math.max(0, response.expiresIn())).minus(RENEW_BEFORE_EXPIRY);
    }

    record TokenResponse(@JsonProperty("access_token") String accessToken, @JsonProperty("expires_in") long expiresIn) {
    }
}
