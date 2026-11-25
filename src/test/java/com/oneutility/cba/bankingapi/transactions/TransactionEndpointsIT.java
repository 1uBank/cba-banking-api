package com.oneutility.cba.bankingapi.transactions;

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

/** Transaction history and look-ups against a fake CBA-CORE, every answer checked against the contract. */
@IntegrationTest
class TransactionEndpointsIT {

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

    private String account(String bvn, String mobile, String ledger, String available) throws Exception {
        long clientId = customer(bvn, mobile);
        String number = JSON.readTree(mvc.perform(get("/v1/customers/{id}/accounts", clientId).with(app()))
                .andReturn().getResponse().getContentAsString()).path("items").get(0).path("accountNumber").asText();
        CORE.setBalances(CORE.savingsId(number), ledger, available);
        return number;
    }

    private void transfer(String from, String to, String amount, String reference) throws Exception {
        send(post("/v1/transfers/intra"), """
                {"sourceAccountNumber":"%s","destinationAccountNumber":"%s","amount":"%s","narration":"Rent",
                 "transactionReference":"%s"}""".formatted(from, to, amount, reference)).andExpect(status().isOk());
    }

    private void debit(String accountNumber, String amount, String reference) throws Exception {
        send(post("/v1/debits"), """
                {"accountNumber":"%s","paymentType":"SMS Fee","amount":"%s","narration":"SMS alerts","transactionReference":"%s"}"""
                .formatted(accountNumber, amount, reference)).andExpect(status().isCreated());
    }

    @Test
    void listsAnAccountsTransactionsNewestFirst() throws Exception {
        String from = account("22200000701", "08000000701", "0", "0");
        String to = account("22200000702", "08000000702", "0", "0");
        CORE.deposit(from, "10000");
        transfer(from, to, "1500.5", "TRF-701");
        debit(from, "25", "DEB-701");

        mvc.perform(get("/v1/accounts/{n}/transactions", from).with(app()))
                .andExpect(status().isOk()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].kind").value("SERVICE_DEBIT"))
                .andExpect(jsonPath("$.items[0].paymentType").value("SMS Fee"))
                .andExpect(jsonPath("$.items[0].transactionReference").value("DEB-701"))
                .andExpect(jsonPath("$.items[0].balanceBefore").value("8499.50"))
                .andExpect(jsonPath("$.items[0].balanceAfter").value("8474.50"))
                .andExpect(jsonPath("$.items[1].kind").value("INTRA_BANK_TRANSFER"))
                .andExpect(jsonPath("$.items[1].transactionType").value("DEBIT"))
                .andExpect(jsonPath("$.items[1].intraBank").value(true))
                .andExpect(jsonPath("$.items[1].counterparty.accountNumber").value(to))
                .andExpect(jsonPath("$.items[1].narration").value("Rent - " + from + " to " + to))
                .andExpect(jsonPath("$.items[2].transactionType").value("CREDIT"))
                .andExpect(jsonPath("$.items[2].kind").value("OTHER"));

        String page = mvc.perform(get("/v1/accounts/{n}/transactions?transactionType=DEBIT&limit=1", from).with(app()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty()).andReturn().getResponse().getContentAsString();
        mvc.perform(get("/v1/accounts/{n}/transactions?transactionType=DEBIT&limit=1&cursor={c}", from,
                JSON.readTree(page).path("nextCursor").asText()).with(app()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].kind").value("INTRA_BANK_TRANSFER"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
        mvc.perform(get("/v1/accounts/{n}/transactions?fromDate=2020-01-01&toDate=2020-01-31", from).with(app()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void showsBothSidesOfATransfer() throws Exception {
        String from = account("22200000703", "08000000703", "0", "0");
        String to = account("22200000704", "08000000704", "0", "0");
        CORE.deposit(from, "500");
        transfer(from, to, "200", "TRF-703");

        String credit = JSON.readTree(mvc.perform(get("/v1/accounts/{n}/transactions", to).with(app())).andReturn().getResponse()
                .getContentAsString()).path("items").get(0).path("transactionId").asText();
        mvc.perform(get("/v1/accounts/{n}/transactions/{id}", to, credit).with(app()))
                .andExpect(status().isOk()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.transactionType").value("CREDIT"))
                .andExpect(jsonPath("$.counterparty.accountNumber").value(from))
                .andExpect(jsonPath("$.counterparty.institutionCode").value("090605"))
                .andExpect(jsonPath("$.transfer.fromAccountNumber").value(from))
                .andExpect(jsonPath("$.transfer.toTransactionId").value(credit));
        mvc.perform(get("/v1/accounts/{n}/transactions/{id}", from, credit).with(app()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));

        mvc.perform(get("/v1/transactions?transactionReference=TRF-703").with(app()))
                .andExpect(status().isOk()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void showsOnlyTheClientsOwnAccounts() throws Exception {
        String mine = account("22200000705", "08000000705", "0", "0");
        long otherClient = customer("22200000706", "08000000706");
        String theirs = JSON.readTree(mvc.perform(get("/v1/customers/{id}/accounts", otherClient).with(app()))
                .andReturn().getResponse().getContentAsString()).path("items").get(0).path("accountNumber").asText();
        CORE.deposit(mine, "500");
        transfer(mine, theirs, "100", "TRF-705");
        CORE.setOwner(otherClient, "irecharge");

        mvc.perform(get("/v1/transactions?transactionReference=TRF-705").with(app()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].accountNumber").value(mine));
        mvc.perform(get("/v1/accounts/{n}/transactions", theirs).with(app()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void refusesInvalidQueries() throws Exception {
        String number = account("22200000707", "08000000707", "0", "0");

        mvc.perform(get("/v1/accounts/{n}/transactions?fromDate=2026-11-10&toDate=2026-11-01", number).with(app()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("fromDate"));
        mvc.perform(get("/v1/transactions").with(app())).andExpect(status().isBadRequest());
        mvc.perform(get("/v1/transactions?sessionId=090605261108101500123456789012").with(app()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
    }
}
