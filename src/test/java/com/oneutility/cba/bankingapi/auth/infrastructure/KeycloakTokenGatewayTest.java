package com.oneutility.cba.bankingapi.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oneutility.cba.bankingapi.auth.application.AuthServiceTokenGateway;
import com.oneutility.cba.bankingapi.auth.application.ClientCredentials;
import com.oneutility.cba.bankingapi.auth.application.IssuedToken;
import com.oneutility.cba.bankingapi.auth.application.TokenRequestException;
import com.oneutility.cba.bankingapi.shared.security.AuthServiceProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

/** The gateway against a stand-in token endpoint on a real local port. */
class KeycloakTokenGatewayTest {

    private static final ClientCredentials APP = new ClientCredentials("app", "s3cret");

    private HttpServer server;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> requestAuthorization = new AtomicReference<>();
    private volatile int status;
    private volatile String responseBody;
    private volatile long delayMillis;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            requestAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            sleep(delayMillis);
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void returnsTheTokenAndSendsTheCredentialsAsBasicAuthentication() {
        respond(200, """
                {"access_token":"eyJ.a.b","expires_in":900,"refresh_expires_in":0,"token_type":"Bearer",
                 "not-before-policy":0,"scope":"customers accounts"}""");

        IssuedToken token = gateway(Duration.ofSeconds(5)).requestToken(APP, "customers");

        assertThat(token).isEqualTo(new IssuedToken("eyJ.a.b", 900, "customers accounts"));
        assertThat(requestBody.get()).isEqualTo("grant_type=client_credentials&scope=customers");
        assertThat(requestAuthorization.get()).isEqualTo("Basic YXBwOnMzY3JldA==");
    }

    @Test
    void turnsAWrongSecretIntoInvalidClient() {
        respond(401, """
                {"error":"unauthorized_client","error_description":"Invalid client or Invalid client credentials"}""");

        assertRefused(HttpStatus.UNAUTHORIZED, "invalid_client");
    }

    @Test
    void passesOnARefusedScope() {
        respond(400, """
                {"error":"invalid_scope","error_description":"Invalid scopes: payments"}""");

        assertRefused(HttpStatus.BAD_REQUEST, "invalid_scope");
    }

    @Test
    void treatsAnUnreadableRefusalAsInvalidClient() {
        respond(400, "not json");

        assertRefused(HttpStatus.UNAUTHORIZED, "invalid_client");
    }

    @Test
    void reportsAnAuthServiceFailureAsUnavailable() {
        respond(503, "{}");

        assertRefused(HttpStatus.SERVICE_UNAVAILABLE, "temporarily_unavailable");
    }

    @Test
    void reportsAnEmptyTokenAsUnavailable() {
        respond(200, "{}");

        assertRefused(HttpStatus.SERVICE_UNAVAILABLE, "temporarily_unavailable");
    }

    @Test
    void timesOut() {
        respond(200, "{\"access_token\":\"late\",\"expires_in\":900}");
        delayMillis = 1500;

        assertThatThrownBy(() -> gateway(Duration.ofMillis(300)).requestToken(APP, null))
                .isInstanceOfSatisfying(TokenRequestException.class,
                        e -> assertThat(e.error()).isEqualTo("temporarily_unavailable"));
    }

    private void assertRefused(HttpStatus expectedStatus, String expectedError) {
        assertThatThrownBy(() -> gateway(Duration.ofSeconds(5)).requestToken(APP, null))
                .isInstanceOfSatisfying(TokenRequestException.class, e -> {
                    assertThat(e.status()).isEqualTo(expectedStatus);
                    assertThat(e.error()).isEqualTo(expectedError);
                });
    }

    private void respond(int status, String body) {
        this.status = status;
        this.responseBody = body;
    }

    private AuthServiceTokenGateway gateway(Duration readTimeout) {
        URI tokenUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/token");
        AuthServiceProperties properties = new AuthServiceProperties(tokenUrl, URI.create("http://127.0.0.1/certs"),
                "http://127.0.0.1/realms/clients", "banking-api", Duration.ofSeconds(2), readTimeout);
        return new KeycloakTokenGateway(RestClient.builder(), properties);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
