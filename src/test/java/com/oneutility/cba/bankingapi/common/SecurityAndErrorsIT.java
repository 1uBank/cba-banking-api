package com.oneutility.cba.bankingapi.common;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oneutility.cba.bankingapi.IntegrationTest;
import com.oneutility.cba.bankingapi.clients.domain.ApiClient;
import com.oneutility.cba.bankingapi.clients.domain.ApiClientRepository;
import com.oneutility.cba.bankingapi.common.exception.ApiException;
import com.oneutility.cba.bankingapi.common.exception.ErrorCode;
import com.oneutility.cba.bankingapi.common.security.CurrentClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The security rules and the error format every module relies on, through a stand-in endpoint. */
@IntegrationTest
@Import(SecurityAndErrorsIT.Endpoints.class)
class SecurityAndErrorsIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApiClientRepository clients;

    @Test
    void refusesACallWithoutAToken() throws Exception {
        mvc.perform(get("/test/customers"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.type").value("https://docs.cba.1ubank.example/problems/unauthorized"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void letsAnActiveClientWithTheScopeThroughAndKnowsWhoItIs() throws Exception {
        mvc.perform(get("/test/customers").with(token("app", "customers")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("app"));
    }

    @Test
    void refusesAClientWithoutTheScope() throws Exception {
        mvc.perform(get("/test/customers").with(token("app", "accounts")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void refusesASuspendedClientEvenWithAValidToken() throws Exception {
        ApiClient suspended = new ApiClient("suspended-on-call", "Suspended");
        suspended.suspend();
        clients.save(suspended);

        mvc.perform(get("/test/customers").with(token("suspended-on-call", "customers")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.detail").value("The client is not active"));
    }

    @Test
    void refusesATokenFromAClientTheRegistryDoesNotKnow() throws Exception {
        mvc.perform(get("/test/customers").with(token("stranger", "customers")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsTheCorrelationIdSentOrANewOne() throws Exception {
        mvc.perform(get("/test/customers").with(token("app", "customers")).header("X-Correlation-Id", "abc-123"))
                .andExpect(header().string("X-Correlation-Id", "abc-123"));
        mvc.perform(get("/test/customers").with(token("app", "customers")).header("X-Correlation-Id", "bad\nvalue"))
                .andExpect(header().string("X-Correlation-Id", org.hamcrest.Matchers.not("bad\nvalue")));
    }

    @Test
    void reportsInvalidFieldsOneByOne() throws Exception {
        mvc.perform(post("/test/customers").with(token("app", "customers")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bvn\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("bvn"))
                .andExpect(jsonPath("$.errors[0].message").value("must be exactly 11 digits"));
    }

    @Test
    void reportsUnreadableJson() throws Exception {
        mvc.perform(post("/test/customers").with(token("app", "customers")).contentType(MediaType.APPLICATION_JSON)
                .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void returnsAModuleErrorWithItsCode() throws Exception {
        mvc.perform(get("/test/accounts/missing").with(token("app", "accounts")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("$.instance").value("/test/accounts/missing"));
    }

    @Test
    void hidesUnexpectedErrors() throws Exception {
        mvc.perform(get("/test/broken").with(token("app", "accounts")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("database password"))));
    }

    @Test
    void answersAnUnknownPathInTheSameFormat() throws Exception {
        mvc.perform(get("/v1/nothing-here").with(token("app", "accounts")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void answersAWrongMethodInTheSameFormat() throws Exception {
        mvc.perform(post("/test/broken").with(token("app", "accounts")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void answersAWrongContentTypeInTheSameFormat() throws Exception {
        mvc.perform(post("/test/customers").with(token("app", "customers")).contentType(MediaType.TEXT_PLAIN)
                .content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    private static JwtRequestPostProcessor token(String clientId, String scope) {
        return jwt().jwt(jwt -> jwt.claim("azp", clientId)).authorities(new SimpleGrantedAuthority("SCOPE_" + scope));
    }

    record NewCustomer(@Pattern(regexp = "\\d{11}", message = "must be exactly 11 digits") String bvn) {
    }

    @TestConfiguration
    static class Endpoints {

        @RestController
        static class StandInController {

            @GetMapping("/test/customers")
            @PreAuthorize("hasAuthority('SCOPE_customers')")
            Map<String, String> customers() {
                return Map.of("clientId", CurrentClient.id());
            }

            @PostMapping("/test/customers")
            @PreAuthorize("hasAuthority('SCOPE_customers')")
            Map<String, String> create(@Valid @RequestBody NewCustomer customer) {
                return Map.of("bvn", customer.bvn());
            }

            @GetMapping("/test/accounts/missing")
            Map<String, String> missing() {
                throw new ApiException(ErrorCode.ACCOUNT_NOT_FOUND, "No such account for this client");
            }

            @GetMapping("/test/broken")
            Map<String, String> broken() {
                throw new IllegalStateException("database password rejected");
            }
        }
    }
}
