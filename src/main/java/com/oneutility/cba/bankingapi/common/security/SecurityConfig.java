package com.oneutility.cba.bankingapi.common.security;

import com.oneutility.cba.bankingapi.common.exception.ErrorCode;
import com.oneutility.cba.bankingapi.common.exception.ProblemWriter;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Every call needs a valid access token from the auth service's {@code clients} realm, checked locally against the
 * realm's keys: signature, expiry, issuer and the {@code banking-api} audience. The token's scopes become
 * {@code SCOPE_<name>} authorities; guard each endpoint with them:
 *
 * <pre>{@code @PreAuthorize("hasAuthority('SCOPE_transfers')")}</pre>
 *
 * Only the token endpoint and the actuator (on its own, private port) are open.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, ActiveClientCheck activeClients, ProblemWriter problems)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(EndpointRequest.toAnyEndpoint()).permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/auth/token").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(server -> server.jwt(jwt -> {
                })
                        .authenticationEntryPoint((request, response, e) -> problems.write(request, response,
                                ErrorCode.UNAUTHORIZED, "The access token is missing, invalid or expired"))
                        .accessDeniedHandler((request, response, e) -> problems.write(request, response,
                                ErrorCode.FORBIDDEN, "The client does not have the scope for this call")))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, e) -> problems.write(request, response,
                                ErrorCode.UNAUTHORIZED, "The access token is missing, invalid or expired")))
                .addFilterAfter(new ActiveClientFilter(activeClients, problems), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(AuthServiceProperties authService) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(authService.jwkSetUri().toString()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(authService.issuer()), audience(authService.audience())));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> audience(String expected) {
        OAuth2Error wrongAudience = new OAuth2Error("invalid_token", "The token is not for this API", null);
        return jwt -> jwt.getAudience() != null && jwt.getAudience().contains(expected)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(wrongAudience);
    }
}
