package com.oneutility.cba.bankingapi.accounts;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oneutility.cba.bankingapi.IntegrationTest;
import com.oneutility.cba.bankingapi.corebanking.FakeCoreBanking;
import java.io.IOException;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** The Accounts endpoints against a fake CBA-CORE, every answer checked against the contract. */
@IntegrationTest
class AccountEndpointsIT {

    private static final String CONTRACT = "api/banking-api.yaml";
    private static final FakeCoreBanking CORE = start();
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    private static FakeCoreBanking start() {
        try {
            return new FakeCoreBanking();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void coreBanking(DynamicPropertyRegistry registry) {
        registry.add("banking-api.core-banking.base-url", CORE::baseUrl);
        registry.add("banking-api.core-banking.token-url", CORE::tokenUrl);
    }

    @AfterEach
    void up() {
        CORE.setDown(false);
    }

    @AfterAll
    static void stop() {
        CORE.close();
    }

    private static RequestPostProcessor client(String clientId, String... scopes) {
        return jwt().jwt(jwt -> jwt.claim("azp", clientId)).authorities(java.util.Arrays.stream(scopes)
                .map(scope -> (GrantedAuthority) new SimpleGrantedAuthority("SCOPE_" + scope)).toList());
    }

    private static RequestPostProcessor app() {
        return client("app", "customers", "accounts");
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(request.with(app()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long customer(String bvn, String mobile) throws Exception {
        String response = send(post("/v1/individual-customers"), """
                {"firstName":"Ada","lastName":"Obi","email":"ada.%s@example.com","mobileNumber":"%s","bvn":"%s",
                 "address":{"addressLine1":"12 Allen Avenue","state":"Lagos"},
                 "productId":1,"kycLevel":2}""".formatted(mobile, mobile, bvn))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(response).path("clientId").asLong();
    }

    private JsonNode open(long clientId, String body) throws Exception {
        return JSON.readTree(send(post("/v1/customers/{id}/accounts", clientId), body).andExpect(status().isCreated())
                .andExpect(openApi().isValid(CONTRACT)).andReturn().getResponse().getContentAsString());
    }

    @Test
    void listsOpensAndReadsACustomersAccounts() throws Exception {
        long clientId = customer("22200000301", "08000000301");

        mvc.perform(get("/v1/customers/{id}/accounts", clientId).with(app()))
                .andExpect(status().isOk()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].accountNumber").value(Matchers.startsWith("002")))
                .andExpect(jsonPath("$.items[0].accountType").value("CUSTOMER"))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.items[0].kycLevel").value(2))
                .andExpect(jsonPath("$.items[0].availableBalance").value("0.00"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());

        JsonNode current = open(clientId, "{\"productId\":3,\"transactionReference\":\"OPEN-301\"}");
        JsonNode again = open(clientId, "{\"productId\":3,\"transactionReference\":\"OPEN-301\"}");
        org.assertj.core.api.Assertions.assertThat(again.path("savingsId")).isEqualTo(current.path("savingsId"));
        org.assertj.core.api.Assertions.assertThat(current.path("accountNumber").asText()).startsWith("005").hasSize(10);
        org.assertj.core.api.Assertions.assertThat(current.path("productName").asText()).isEqualTo("Current Account");

        String number = current.path("accountNumber").asText();
        CORE.setBalances(CORE.savingsId(number), "1500.5", "1000");
        mvc.perform(get("/v1/accounts/{n}", number).with(app()))
                .andExpect(status().isOk()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.clientId").value(clientId))
                .andExpect(jsonPath("$.accountName").value("Ada Obi"))
                .andExpect(jsonPath("$.currentBalance").value("1500.50"))
                .andExpect(jsonPath("$.availableBalance").value("1000.00"))
                .andExpect(jsonPath("$.currency").value("NGN"));

        mvc.perform(get("/v1/customers/{id}/accounts?limit=1", clientId).with(app()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty());
    }

    @Test
    void reportsEachAccountState() throws Exception {
        long clientId = customer("22200000302", "08000000302");
        long dormant = open(clientId, "{\"productId\":1}").path("savingsId").asLong();
        String blocked = open(clientId, "{\"productId\":1}").path("accountNumber").asText();
        String closed = open(clientId, "{\"productId\":1}").path("accountNumber").asText();
        String pending = open(clientId, "{\"productId\":1}").path("accountNumber").asText();
        CORE.setAccountState(dormant, "dormant");
        CORE.setAccountState(CORE.savingsId(blocked), "blocked");
        CORE.setAccountState(CORE.savingsId(closed), "closed");
        CORE.setAccountState(CORE.savingsId(pending), "pending");

        mvc.perform(get("/v1/customers/{id}/accounts", clientId).with(app()))
                .andExpect(status().isOk()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.items[*].status").value(Matchers.contains("ACTIVE", "DORMANT", "RESTRICTED", "CLOSED")));
        mvc.perform(get("/v1/accounts/{n}", blocked).with(app()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESTRICTED"));
        for (String gone : new String[] { closed, pending }) {
            mvc.perform(get("/v1/accounts/{n}", gone).with(app()))
                    .andExpect(status().isNotFound()).andExpect(openApi().isValid(CONTRACT))
                    .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        }
    }

    @Test
    void findsAccountsByBvnPageByPage() throws Exception {
        long clientId = customer("22200000303", "08000000303");
        open(clientId, "{\"productId\":3}");

        String first = send(post("/v1/accounts/search?limit=1"), "{\"bvn\":\"22200000303\"}")
                .andExpect(status().isOk()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty()).andReturn().getResponse().getContentAsString();
        send(post("/v1/accounts/search?limit=1&cursor=" + JSON.readTree(first).path("nextCursor").asText()),
                "{\"bvn\":\"22200000303\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].accountNumber").value(Matchers.startsWith("005")))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
        send(post("/v1/accounts/search"), "{\"bvn\":\"22299999999\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void hidesOtherClientsCustomersAndAccounts() throws Exception {
        long clientId = customer("22200000304", "08000000304");
        String number = open(clientId, "{\"productId\":3}").path("accountNumber").asText();
        CORE.setOwner(clientId, "irecharge");

        mvc.perform(get("/v1/customers/{id}/accounts", clientId).with(app()))
                .andExpect(status().isNotFound()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
        send(post("/v1/customers/{id}/accounts", clientId), "{\"productId\":3}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
        mvc.perform(get("/v1/accounts/{n}", number).with(app()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        send(post("/v1/accounts/search"), "{\"bvn\":\"22200000304\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void refusesInvalidRequests() throws Exception {
        long clientId = customer("22200000305", "08000000305");

        send(post("/v1/customers/{id}/accounts", clientId), "{\"productId\":99}")
                .andExpect(status().isBadRequest()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.errors[0].field").value("productId"));
        send(post("/v1/customers/{id}/accounts", clientId), "{\"productId\":3,\"clientId\":1}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("clientId"));
        send(post("/v1/customers/{id}/accounts", clientId), "{}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/v1/accounts/{n}", "123").with(app()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("accountNumber"));
        mvc.perform(get("/v1/accounts/{n}", "9999999999").with(app()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        mvc.perform(get("/v1/customers/{id}/accounts?limit=0", clientId).with(app()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("limit"));
        mvc.perform(get("/v1/customers/{id}/accounts?cursor=nope", clientId).with(app()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("cursor"));
        send(post("/v1/accounts/search"), "{\"bvn\":\"123\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("bvn"));
    }

    @Test
    void listsProducts() throws Exception {
        mvc.perform(get("/v1/products").with(app()))
                .andExpect(status().isOk()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$[*].id").value(Matchers.hasItems(1, 3)))
                .andExpect(jsonPath("$[0].name").isNotEmpty());
    }

    @Test
    void reportsTheCoreLedgerBeingUnreachable() throws Exception {
        CORE.setDown(true);
        mvc.perform(get("/v1/products").with(app()))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("UPSTREAM_UNAVAILABLE"));
    }

    @Test
    void needsTheAccountsScope() throws Exception {
        mvc.perform(get("/v1/products").with(client("app", "customers")))
                .andExpect(status().isForbidden()).andExpect(openApi().isValid(CONTRACT));
    }
}
