package com.oneutility.cba.bankingapi.shared.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Where the auth service's {@code clients} realm is, as the Banking API reaches it. The service does not start without
 * these.
 *
 * @param tokenUrl the realm's token endpoint, where client token requests are forwarded
 * @param jwkSetUri the realm's signing keys, used to check tokens locally on every call
 * @param issuer the {@code iss} every token must carry: the realm's public address, which can differ from the address
 *        the Banking API uses to reach it
 * @param audience the {@code aud} every token must carry
 * @param connectTimeout connection timeout for calls to the auth service
 * @param readTimeout response timeout for calls to the auth service
 */
@Validated
@ConfigurationProperties(prefix = "banking-api.auth-service")
public record AuthServiceProperties(@NotNull URI tokenUrl, @NotNull URI jwkSetUri, @NotBlank String issuer,
        @NotBlank String audience, @NotNull Duration connectTimeout, @NotNull Duration readTimeout) {
}
