package com.oneutility.cba.bankingapi.virtualaccounts;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

/** Static virtual accounts against a fake CBA-CORE, every answer checked against the contract. */
@IntegrationTest
class VirtualAccountEndpointsIT {

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
        return client("app", "customers", "accounts", "transfers", "virtual-accounts");
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

    private String parent(String bvn, String mobile) throws Exception {
        long clientId = customer(bvn, mobile);
        return JSON.readTree(mvc.perform(get("/v1/customers/{id}/accounts", clientId).with(app()))
                .andReturn().getResponse().getContentAsString()).path("items").get(0).path("accountNumber").asText();
    }

    private JsonNode create(String body) throws Exception {
        return JSON.readTree(send(post("/v1/virtual-accounts"), body).andExpect(status().isCreated())
                .andExpect(openApi().isValid(CONTRACT)).andReturn().getResponse().getContentAsString());
    }

    @Test
    void createsReadsRenamesAndClosesAVirtualAccount() throws Exception {
        String parent = parent("22200000501", "08000000501");

        JsonNode va = create("""
                {"label":"Rent","parentAccountNumber":"%s","type":"SMART_TRANSFER","transactionReference":"VA-501"}"""
                .formatted(parent));
        String number = va.path("accountNumber").asText();
        org.assertj.core.api.Assertions.assertThat(number).startsWith("600").hasSize(10);
        org.assertj.core.api.Assertions.assertThat(va.path("name").asText()).isEqualTo("Ada Obi - Rent");
        org.assertj.core.api.Assertions.assertThat(va.path("parentAccountNumber").asText()).isEqualTo(parent);

        // The same request again returns the same virtual account
        org.assertj.core.api.Assertions.assertThat(create("""
                {"label":"Rent","parentAccountNumber":"%s","type":"SMART_TRANSFER","transactionReference":"VA-501"}"""
                .formatted(parent)).path("accountNumber").asText()).isEqualTo(number);

        mvc.perform(get("/v1/virtual-accounts/{n}", number).with(app()))
                .andExpect(status().isOk()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.status").value("ACTIVE")).andExpect(jsonPath("$.type").value("SMART_TRANSFER"));
        send(patch("/v1/virtual-accounts/{n}", number), "{\"label\":\"School fees\"}")
                .andExpect(status().isOk()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.name").value("Ada Obi - School fees"));

        // Its balance and blocks are those of its account
        mvc.perform(get("/v1/accounts/{n}", number).with(app())).andExpect(status().isOk());
        CORE.setBalances(CORE.savingsId(number), "50", "50");
        mvc.perform(post("/v1/virtual-accounts/{n}/close", number).with(app()))
                .andExpect(status().isUnprocessableEntity()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_EMPTY"));
        CORE.setBalances(CORE.savingsId(number), "0", "0");
        send(post("/v1/accounts/{n}/blocks", number), "{\"type\":\"CREDIT\",\"reason\":\"Paused\"}")
                .andExpect(status().isCreated());
        mvc.perform(post("/v1/virtual-accounts/{n}/close", number).with(app()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("ACCOUNT_RESTRICTED"));
        mvc.perform(post("/v1/accounts/{n}/blocks/{t}/lift", number, "CREDIT").with(app())).andExpect(status().isOk());

        mvc.perform(post("/v1/virtual-accounts/{n}/close", number).with(app()))
                .andExpect(status().isOk()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.status").value("CLOSED"));
        mvc.perform(post("/v1/virtual-accounts/{n}/close", number).with(app()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
        send(patch("/v1/virtual-accounts/{n}", number), "{\"label\":\"Too late\"}")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));
    }

    @Test
    void anAirtimeAccountTakesThePhoneNumber() throws Exception {
        String parent = parent("22200000502", "08000000502");

        JsonNode va = create("""
                {"label":"Airtime","parentAccountNumber":"%s","type":"AIRTIME","accountNumber":"8030000502"}""".formatted(parent));
        org.assertj.core.api.Assertions.assertThat(va.path("accountNumber").asText()).isEqualTo("8030000502");

        send(post("/v1/virtual-accounts"), """
                {"label":"Airtime","parentAccountNumber":"%s","type":"AIRTIME","accountNumber":"8030000502"}""".formatted(parent))
                .andExpect(status().isConflict()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.code").value("ACCOUNT_NUMBER_TAKEN"));
        send(post("/v1/virtual-accounts"), """
                {"label":"Airtime","parentAccountNumber":"%s","type":"AIRTIME"}""".formatted(parent))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("accountNumber"));
        send(post("/v1/virtual-accounts"), """
                {"label":"Rent","parentAccountNumber":"%s","type":"SMART_TRANSFER","accountNumber":"8030000999"}""".formatted(parent))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("accountNumber"));
    }

    @Test
    void refusesATypeTheBankHasNoProductFor() throws Exception {
        String parent = parent("22200000503", "08000000503");

        send(post("/v1/virtual-accounts"), """
                {"label":"DStv","parentAccountNumber":"%s","type":"CABLE_TV"}""".formatted(parent))
                .andExpect(status().isUnprocessableEntity()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.code").value("VIRTUAL_ACCOUNT_TYPE_UNAVAILABLE"));
    }

    @Test
    void listsTheClientsVirtualAccountsPageByPage() throws Exception {
        String parent = parent("22200000504", "08000000504");
        create("{\"label\":\"A\",\"parentAccountNumber\":\"%s\",\"type\":\"SMART_TRANSFER\"}".formatted(parent));
        create("{\"label\":\"B\",\"parentAccountNumber\":\"%s\",\"type\":\"SMART_TRANSFER\"}".formatted(parent));
        create("{\"label\":\"C\",\"parentAccountNumber\":\"%s\",\"type\":\"AIRTIME\",\"accountNumber\":\"8030000504\"}"
                .formatted(parent));

        String first = mvc.perform(get("/v1/virtual-accounts?parentAccountNumber={p}&type=SMART_TRANSFER&limit=1", parent).with(app()))
                .andExpect(status().isOk()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].label").value("A"))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty()).andReturn().getResponse().getContentAsString();
        mvc.perform(get("/v1/virtual-accounts?parentAccountNumber={p}&type=SMART_TRANSFER&limit=1&cursor={c}", parent,
                JSON.readTree(first).path("nextCursor").asText()).with(app()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].label").value("B"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
        mvc.perform(get("/v1/virtual-accounts?parentAccountNumber={p}", parent).with(app()))
                .andExpect(jsonPath("$.items.length()").value(3));
    }

    @Test
    void hidesOtherClientsVirtualAccounts() throws Exception {
        long clientId = customer("22200000505", "08000000505");
        String parent = JSON.readTree(mvc.perform(get("/v1/customers/{id}/accounts", clientId).with(app()))
                .andReturn().getResponse().getContentAsString()).path("items").get(0).path("accountNumber").asText();
        String number = create("{\"label\":\"X\",\"parentAccountNumber\":\"%s\",\"type\":\"SMART_TRANSFER\"}".formatted(parent))
                .path("accountNumber").asText();
        CORE.setOwner(clientId, "irecharge");

        mvc.perform(get("/v1/virtual-accounts/{n}", number).with(app()))
                .andExpect(status().isNotFound()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        send(post("/v1/virtual-accounts"), "{\"label\":\"Y\",\"parentAccountNumber\":\"%s\",\"type\":\"SMART_TRANSFER\"}"
                .formatted(parent)).andExpect(status().isNotFound());
        mvc.perform(post("/v1/virtual-accounts/{n}/close", number).with(app())).andExpect(status().isNotFound());
    }

    @Test
    void needsTheVirtualAccountsScope() throws Exception {
        mvc.perform(get("/v1/virtual-accounts").with(client("app", "accounts")))
                .andExpect(status().isForbidden()).andExpect(openApi().isValid(CONTRACT));
    }
}
