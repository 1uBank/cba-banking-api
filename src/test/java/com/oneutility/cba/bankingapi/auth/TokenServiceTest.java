package com.oneutility.cba.bankingapi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.oneutility.cba.bankingapi.auth.application.AuthServiceTokenGateway;
import com.oneutility.cba.bankingapi.auth.application.ClientCredentials;
import com.oneutility.cba.bankingapi.auth.application.IssuedToken;
import com.oneutility.cba.bankingapi.auth.application.TokenRequestException;
import com.oneutility.cba.bankingapi.auth.application.TokenService;
import com.oneutility.cba.bankingapi.shared.security.ActiveClientCheck;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenServiceTest {

    private final ActiveClientCheck activeClients = mock(ActiveClientCheck.class);
    private final AuthServiceTokenGateway authService = mock(AuthServiceTokenGateway.class);
    private final TokenService tokens = new TokenService(activeClients, authService);

    @BeforeEach
    void activeApp() {
        when(activeClients.isActive("app")).thenReturn(true);
        when(authService.requestToken(any(), any())).thenReturn(new IssuedToken("token", 900, "customers"));
    }

    @Test
    void issuesATokenForFormCredentials() {
        IssuedToken token = tokens.issue("client_credentials", "app", "s3cret", null, null);

        assertThat(token.accessToken()).isEqualTo("token");
        verify(authService).requestToken(new ClientCredentials("app", "s3cret"), null);
    }

    @Test
    void issuesATokenForBasicCredentialsAndPassesTheScope() {
        tokens.issue("client_credentials", null, null, basic("app", "s3cret"), "customers");

        verify(authService).requestToken(new ClientCredentials("app", "s3cret"), "customers");
    }

    @Test
    void decodesFormEncodedBasicCredentials() {
        tokens.issue("client_credentials", null, null, basic("app", "a%3Ab+c"), null);

        verify(authService).requestToken(new ClientCredentials("app", "a:b c"), null);
    }

    @Test
    void refusesAMissingGrantType() {
        assertRefused(() -> tokens.issue(null, "app", "s3cret", null, null), "invalid_request");
    }

    @Test
    void refusesAnyOtherGrantType() {
        assertRefused(() -> tokens.issue("password", "app", "s3cret", null, null), "unsupported_grant_type");
    }

    @Test
    void refusesCredentialsSentTwoWays() {
        assertRefused(() -> tokens.issue("client_credentials", "app", "s3cret", basic("app", "s3cret"), null),
                "invalid_request");
    }

    @Test
    void refusesMissingCredentials() {
        assertRefused(() -> tokens.issue("client_credentials", "app", null, null, null), "invalid_client");
        assertRefused(() -> tokens.issue("client_credentials", null, "s3cret", null, null), "invalid_client");
    }

    @Test
    void refusesMalformedBasicCredentials() {
        assertRefused(() -> tokens.issue("client_credentials", null, null, "Basic !!!", null), "invalid_client");
        String noSecret = "Basic " + Base64.getEncoder().encodeToString("app:".getBytes(StandardCharsets.UTF_8));
        assertRefused(() -> tokens.issue("client_credentials", null, null, noSecret, null), "invalid_client");
    }

    @Test
    void refusesAClientThatIsNotActiveWithoutAskingTheAuthService() {
        assertRefused(() -> tokens.issue("client_credentials", "irecharge", "s3cret", null, null), "invalid_client");

        verifyNoInteractions(authService);
    }

    private static void assertRefused(Runnable call, String error) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(TokenRequestException.class,
                e -> assertThat(e.error()).isEqualTo(error));
    }

    private static String basic(String id, String secret) {
        return "Basic " + Base64.getEncoder().encodeToString((id + ":" + secret).getBytes(StandardCharsets.UTF_8));
    }
}
