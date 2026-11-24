package com.oneutility.cba.bankingapi.onus;

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

/** Holds and blocks against a fake CBA-CORE, every answer checked against the contract. */
@IntegrationTest
class HoldAndBlockEndpointsIT {

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
        return client("app", "customers", "accounts", "transfers");
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

    private String account(String bvn, String mobile, String ledger, String available) throws Exception {
        long clientId = customer(bvn, mobile);
        String number = JSON.readTree(mvc.perform(get("/v1/customers/{id}/accounts", clientId).with(app()))
                .andReturn().getResponse().getContentAsString()).path("items").get(0).path("accountNumber").asText();
        CORE.setBalances(CORE.savingsId(number), ledger, available);
        return number;
    }

    private String hold(String accountNumber, String amount, String reference) {
        return """
                {"accountNumber":"%s","amount":"%s","reason":"Electricity token purchase","transactionReference":"%s"}"""
                .formatted(accountNumber, amount, reference);
    }

    @Test
    void placesReadsAndReleasesAHold() throws Exception {
        String number = account("22200000401", "08000000401", "10000", "10000");

        send(post("/v1/holds"), hold(number, "2500", "LIEN-401"))
                .andExpect(status().isCreated()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.transactionReference").value("LIEN-401"))
                .andExpect(jsonPath("$.accountNumber").value(number))
                .andExpect(jsonPath("$.amount").value("2500.00"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdDate").value("2026-11-23T10:15:00+01:00"));
        mvc.perform(get("/v1/accounts/{n}", number).with(app()))
                .andExpect(jsonPath("$.availableBalance").value("7500.00"));

        // The same request again returns the same hold; the reference with another request is refused
        send(post("/v1/holds"), hold(number, "2500", "LIEN-401")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value("2500.00"));
        send(post("/v1/holds"), hold(number, "99", "LIEN-401"))
                .andExpect(status().isConflict()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.code").value("REFERENCE_REUSED"));

        mvc.perform(get("/v1/holds/{ref}", "LIEN-401").with(app()))
                .andExpect(status().isOk()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        send(post("/v1/holds/release"), "{\"transactionReference\":\"LIEN-401\"}")
                .andExpect(status().isOk()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.status").value("RELEASED"))
                .andExpect(jsonPath("$.releasedDate").value("2026-11-23T11:00:00+01:00"));
        send(post("/v1/holds/release"), "{\"transactionReference\":\"LIEN-401\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RELEASED"));
        mvc.perform(get("/v1/accounts/{n}", number).with(app()))
                .andExpect(jsonPath("$.availableBalance").value("10000.00"));
    }

    @Test
    void releasesAHoldPlacedElsewhereByItsTransactionId() throws Exception {
        String number = account("22200000402", "08000000402", "5000", "5000");
        long holdId = CORE.placeHoldWithoutReference(number, "1000");

        send(post("/v1/holds/release"), "{\"accountNumber\":\"%s\",\"holdTransactionId\":%d}".formatted(number, holdId))
                .andExpect(status().isOk()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.holdTransactionId").value(holdId))
                .andExpect(jsonPath("$.transactionReference").doesNotExist())
                .andExpect(jsonPath("$.status").value("RELEASED"));
    }

    @Test
    void refusesHoldsTheAccountCannotTake() throws Exception {
        String number = account("22200000403", "08000000403", "100", "100");

        send(post("/v1/holds"), hold(number, "500", "LIEN-403"))
                .andExpect(status().isUnprocessableEntity()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        send(post("/v1/holds"), hold("9999999999", "1", "LIEN-403B"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        mvc.perform(get("/v1/holds/{ref}", "NO-SUCH-HOLD").with(app()))
                .andExpect(status().isNotFound()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.code").value("HOLD_NOT_FOUND"));
        send(post("/v1/holds/release"), "{\"transactionReference\":\"X\",\"accountNumber\":\"%s\"}".formatted(number))
                .andExpect(status().isBadRequest());
    }

    @Test
    void hidesOtherClientsHolds() throws Exception {
        long clientId = customer("22200000404", "08000000404");
        String number = JSON.readTree(mvc.perform(get("/v1/customers/{id}/accounts", clientId).with(app()))
                .andReturn().getResponse().getContentAsString()).path("items").get(0).path("accountNumber").asText();
        CORE.setBalances(CORE.savingsId(number), "1000", "1000");
        send(post("/v1/holds"), hold(number, "10", "LIEN-404")).andExpect(status().isCreated());
        CORE.setOwner(clientId, "irecharge");

        mvc.perform(get("/v1/holds/{ref}", "LIEN-404").with(app()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("HOLD_NOT_FOUND"));
        send(post("/v1/holds/release"), "{\"transactionReference\":\"LIEN-404\"}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("HOLD_NOT_FOUND"));
    }

    @Test
    void blocksAndLiftsInAnyCombination() throws Exception {
        String number = account("22200000405", "08000000405", "0", "0");

        mvc.perform(get("/v1/accounts/{n}/blocks", number).with(app()))
                .andExpect(status().isOk()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.block").value("NONE")).andExpect(jsonPath("$.reason").doesNotExist());
        send(post("/v1/accounts/{n}/blocks", number), "{\"type\":\"DEBIT\",\"reason\":\"Fraud check\"}")
                .andExpect(status().isCreated()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.block").value("DEBIT")).andExpect(jsonPath("$.reason").value("Fraud check"));
        send(post("/v1/accounts/{n}/blocks", number), "{\"type\":\"DEBIT\",\"reason\":\"Again\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.block").value("DEBIT"));
        send(post("/v1/accounts/{n}/blocks", number), "{\"type\":\"CREDIT\",\"reason\":\"Cashier on leave\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.block").value("ALL"));
        mvc.perform(get("/v1/accounts/{n}", number).with(app())).andExpect(jsonPath("$.status").value("RESTRICTED"));

        mvc.perform(post("/v1/accounts/{n}/blocks/{type}/lift", number, "DEBIT").with(app()))
                .andExpect(status().isOk()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.block").value("CREDIT"));
        mvc.perform(post("/v1/accounts/{n}/blocks/{type}/lift", number, "DEBIT").with(app()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.block").value("CREDIT"));
        mvc.perform(post("/v1/accounts/{n}/blocks/{type}/lift", number, "ALL").with(app()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.block").value("NONE"));
        send(post("/v1/accounts/{n}/blocks", number), "{\"type\":\"ALL\",\"reason\":\"Court order\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.block").value("ALL"));
        mvc.perform(post("/v1/accounts/{n}/blocks/{type}/lift", number, "CREDIT").with(app()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.block").value("DEBIT"));
    }

    @Test
    void refusesInvalidBlocks() throws Exception {
        String number = account("22200000406", "08000000406", "0", "0");

        send(post("/v1/accounts/{n}/blocks", number), "{\"type\":\"DEBIT\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("reason"));
        mvc.perform(post("/v1/accounts/{n}/blocks/{type}/lift", number, "SIDEWAYS").with(app()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/v1/accounts/{n}/blocks", "9999999999").with(app()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }
}
