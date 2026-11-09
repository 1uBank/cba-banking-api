package com.oneutility.cba.bankingapi.shared.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * The client application making the current call. Modules use it for ownership checks: a client sees and moves money
 * only on accounts it owns.
 */
public final class CurrentClient {

    /** Keycloak puts the client's ID in {@code azp} on client-credentials tokens. */
    static final String CLIENT_ID_CLAIM = "azp";

    private CurrentClient() {
    }

    /** The calling client's ID. Fails if called outside an authenticated request. */
    public static String id() {
        return find().orElseThrow(() -> new IllegalStateException("No authenticated client on this thread"));
    }

    static Optional<String> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            return clientId(token.getToken());
        }
        return Optional.empty();
    }

    static Optional<String> clientId(Jwt jwt) {
        return Optional.ofNullable(jwt.getClaimAsString(CLIENT_ID_CLAIM)).filter(id -> !id.isBlank());
    }
}
