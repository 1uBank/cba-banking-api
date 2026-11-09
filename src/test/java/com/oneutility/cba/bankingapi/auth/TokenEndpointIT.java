package com.oneutility.cba.bankingapi.auth;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.oneutility.cba.bankingapi.IntegrationTest;
import com.oneutility.cba.bankingapi.auth.application.AuthServiceTokenGateway;
import com.oneutility.cba.bankingapi.auth.application.ClientCredentials;
import com.oneutility.cba.bankingapi.auth.application.IssuedToken;
import com.oneutility.cba.bankingapi.auth.application.TokenRequestException;
import com.oneutility.cba.bankingapi.clients.domain.ApiClient;
import com.oneutility.cba.bankingapi.clients.infrastructure.ApiClientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** {@code POST /v1/auth/token} end to end, checked against the contract in {@code api/banking-api.yaml}. */
@IntegrationTest
class TokenEndpointIT {

    private static final String CONTRACT = "api/banking-api.yaml";

    // For requests that break the contract on purpose: only the response is checked.
    private static final OpenApiInteractionValidator RESPONSE_CONTRACT = OpenApiInteractionValidator
            .createFor(CONTRACT)
            .withLevelResolver(LevelResolver.create()
                    .withLevel("validation.request", ValidationReport.Level.IGNORE).build())
            .build();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApiClientRepository clients;

    @MockitoBean
    private AuthServiceTokenGateway authService;

    @Test
    void issuesATokenToAnActiveClient() throws Exception {
        when(authService.requestToken(eq(new ClientCredentials("app", "s3cret")), any()))
                .thenReturn(new IssuedToken("eyJ.a.b", 900, "customers accounts"));

        mvc.perform(post("/v1/auth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("grant_type=client_credentials&client_id=app&client_secret=s3cret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("eyJ.a.b"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(900))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(openApi().isValid(CONTRACT));
    }

    @Test
    void refusesASuspendedClient() throws Exception {
        ApiClient suspended = new ApiClient("suspended-at-token", "Suspended");
        suspended.suspend();
        clients.save(suspended);

        mvc.perform(post("/v1/auth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("grant_type=client_credentials&client_id=suspended-at-token&client_secret=s3cret"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"))
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(openApi().isValid(CONTRACT));
    }

    @Test
    void refusesAnUnknownClient() throws Exception {
        mvc.perform(post("/v1/auth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("grant_type=client_credentials&client_id=nobody&client_secret=x"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));
    }

    @Test
    void refusesAnotherGrantType() throws Exception {
        mvc.perform(post("/v1/auth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("grant_type=password&client_id=app&client_secret=s3cret"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unsupported_grant_type"))
                .andExpect(openApi().isValid(RESPONSE_CONTRACT));
    }

    @Test
    void saysWhenTheAuthServiceIsDown() throws Exception {
        when(authService.requestToken(any(), any())).thenThrow(TokenRequestException.unavailable());

        mvc.perform(post("/v1/auth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("grant_type=client_credentials&client_id=app&client_secret=s3cret"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("temporarily_unavailable"))
                .andExpect(openApi().isValid(CONTRACT));
    }
}
