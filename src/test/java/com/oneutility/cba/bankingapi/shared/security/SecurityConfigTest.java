package com.oneutility.cba.bankingapi.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityConfigTest {

    @Test
    void acceptsATokenForTheBankingApi() {
        assertThat(SecurityConfig.audience("banking-api").validate(token(List.of("banking-api", "other")))
                .hasErrors()).isFalse();
    }

    @Test
    void refusesATokenForAnotherService() {
        assertThat(SecurityConfig.audience("banking-api").validate(token(List.of("cba-core"))).hasErrors()).isTrue();
    }

    @Test
    void readsTheClientIdFromAzp() {
        Jwt jwt = token(List.of("banking-api"));
        assertThat(CurrentClient.clientId(jwt)).contains("app");
    }

    private static Jwt token(List<String> audience) {
        return Jwt.withTokenValue("t").header("alg", "RS256").audience(audience).claim("azp", "app")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }
}
