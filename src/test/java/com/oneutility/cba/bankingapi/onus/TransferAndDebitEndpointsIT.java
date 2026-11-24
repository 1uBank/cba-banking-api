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

/** Intra-bank transfers and service debits against a fake CBA-CORE, every answer checked against the contract. */
@IntegrationTest
class TransferAndDebitEndpointsIT {

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

    private String transfer(String from, String to, String amount, String reference) {
        return """
                {"sourceAccountNumber":"%s","destinationAccountNumber":"%s","amount":"%s","narration":"Rent",
                 "transactionReference":"%s"}""".formatted(from, to, amount, reference);
    }

    private String balance(String accountNumber) throws Exception {
        return JSON.readTree(mvc.perform(get("/v1/accounts/{n}", accountNumber).with(app())).andReturn().getResponse()
                .getContentAsString()).path("availableBalance").asText();
    }

    @Test
    void transfersBetweenTwoAccountsOnce() throws Exception {
        String from = account("22200000601", "08000000601", "10000", "10000");
        String to = account("22200000602", "08000000602", "0", "0");

        send(post("/v1/transfers/intra"), transfer(from, to, "1500.5", "TRF-601"))
                .andExpect(status().isOk()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.transactionStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.transferType").value("INTRA_BANK"))
                .andExpect(jsonPath("$.amount").value("1500.50")).andExpect(jsonPath("$.fee").value("0.00"))
                .andExpect(jsonPath("$.destinationInstitutionCode").value("090605"))
                .andExpect(jsonPath("$.destinationAccountName").value("Ada Obi"))
                .andExpect(jsonPath("$.narration").value("Rent - " + from + " to " + to))
                .andExpect(jsonPath("$.transactionDate").value("2026-11-24T09:30:00+01:00"));
        send(post("/v1/transfers/intra"), transfer(from, to, "1500.5", "TRF-601")).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(balance(from)).isEqualTo("8499.50");
        org.assertj.core.api.Assertions.assertThat(balance(to)).isEqualTo("1500.50");

        send(post("/v1/transfers/intra"), transfer(from, to, "1", "TRF-601"))
                .andExpect(status().isConflict()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.code").value("REFERENCE_REUSED"));
        mvc.perform(get("/v1/transfers/{ref}", "TRF-601").with(app()))
                .andExpect(status().isOk()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.transactionId").isNotEmpty());
        mvc.perform(get("/v1/transfers/{ref}", "NO-SUCH").with(app()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TRANSFER_NOT_FOUND"));
    }

    @Test
    void refusesTransfersTheAccountsCannotMake() throws Exception {
        String from = account("22200000603", "08000000603", "100", "100");
        String to = account("22200000604", "08000000604", "0", "0");

        send(post("/v1/transfers/intra"), transfer(from, to, "500", "TRF-603A"))
                .andExpect(status().isUnprocessableEntity()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        send(post("/v1/accounts/{n}/blocks", to), "{\"type\":\"CREDIT\",\"reason\":\"Court order\"}").andExpect(status().isCreated());
        send(post("/v1/transfers/intra"), transfer(from, to, "10", "TRF-603B"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("ACCOUNT_RESTRICTED"));
        send(post("/v1/transfers/intra"), transfer(from, "9999999999", "10", "TRF-603C"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        send(post("/v1/transfers/intra"), transfer(from, from, "10", "TRF-603D"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("destinationAccountNumber"));
        send(post("/v1/transfers/intra"), transfer(from, to, "10", "TRF-603E").replace("\"narration\"",
                "\"destinationAccountName\":\"Someone Else\",\"narration\""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("destinationAccountName"));
        org.assertj.core.api.Assertions.assertThat(balance(from)).isEqualTo("100.00");
    }

    @Test
    void moneyInAVirtualAccountOnlyGoesToItsParent() throws Exception {
        String parent = account("22200000605", "08000000605", "0", "0");
        String other = account("22200000606", "08000000606", "0", "0");
        String va = JSON.readTree(send(post("/v1/virtual-accounts"),
                "{\"label\":\"Rent\",\"parentAccountNumber\":\"%s\",\"type\":\"SMART_TRANSFER\"}".formatted(parent))
                .andReturn().getResponse().getContentAsString()).path("accountNumber").asText();
        CORE.setBalances(CORE.savingsId(va), "300", "300");

        send(post("/v1/transfers/intra"), transfer(va, other, "100", "TRF-605A"))
                .andExpect(status().isUnprocessableEntity()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.code").value("ACCOUNT_RESTRICTED"));
        send(post("/v1/transfers/intra"), transfer(va, parent, "300", "TRF-605B"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.transactionStatus").value("CONFIRMED"));
        org.assertj.core.api.Assertions.assertThat(balance(parent)).isEqualTo("300.00");
        send(post("/v1/debits"), debit(va, "SMS Fee", "1", "DEB-605"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("ACCOUNT_RESTRICTED"));
    }

    private static String debit(String accountNumber, String paymentType, String amount, String reference) {
        return """
                {"accountNumber":"%s","paymentType":"%s","amount":"%s","narration":"SMS alerts for November",
                 "transactionReference":"%s"}""".formatted(accountNumber, paymentType, amount, reference);
    }

    @Test
    void debitsWithAnyPaymentTypeOnce() throws Exception {
        String number = account("22200000607", "08000000607", "1000", "1000");

        mvc.perform(get("/v1/payment-types").with(app()))
                .andExpect(status().isOk()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$[*].name").value(Matchers.hasItems("Money Transfer", "SMS Fee")));
        send(post("/v1/debits"), debit(number, "sms fee", "25", "DEB-607"))
                .andExpect(status().isCreated()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.paymentType").value("SMS Fee")).andExpect(jsonPath("$.amount").value("25.00"))
                .andExpect(jsonPath("$.transactionStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.narration").value("SMS alerts for November"));
        send(post("/v1/debits"), debit(number, "SMS Fee", "25", "DEB-607")).andExpect(status().isCreated());
        org.assertj.core.api.Assertions.assertThat(balance(number)).isEqualTo("975.00");

        send(post("/v1/debits"), debit(number, "SMS Fee", "30", "DEB-607"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REFERENCE_REUSED"));
        // The same reference on another account: nothing is posted there
        String other = account("22200000610", "08000000610", "100", "100");
        send(post("/v1/debits"), debit(other, "SMS Fee", "25", "DEB-607"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REFERENCE_REUSED"));
        org.assertj.core.api.Assertions.assertThat(balance(other)).isEqualTo("100.00");
        send(post("/v1/debits"), debit(number, "Bitcoin", "1", "DEB-607B"))
                .andExpect(status().isUnprocessableEntity()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.code").value("PAYMENT_TYPE_UNKNOWN"));
        send(post("/v1/debits"), debit(number, "SMS Fee", "5000", "DEB-607C"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void hidesOtherClientsTransfers() throws Exception {
        long clientId = customer("22200000608", "08000000608");
        String from = JSON.readTree(mvc.perform(get("/v1/customers/{id}/accounts", clientId).with(app()))
                .andReturn().getResponse().getContentAsString()).path("items").get(0).path("accountNumber").asText();
        CORE.setBalances(CORE.savingsId(from), "100", "100");
        String to = account("22200000609", "08000000609", "0", "0");
        send(post("/v1/transfers/intra"), transfer(from, to, "10", "TRF-608")).andExpect(status().isOk());
        CORE.setOwner(clientId, "irecharge");

        mvc.perform(get("/v1/transfers/{ref}", "TRF-608").with(app()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TRANSFER_NOT_FOUND"));
        send(post("/v1/transfers/intra"), transfer(from, to, "10", "TRF-608B"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }
}
