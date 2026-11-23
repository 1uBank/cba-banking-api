package com.oneutility.cba.bankingapi.customers;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oneutility.cba.bankingapi.IntegrationTest;
import com.oneutility.cba.bankingapi.corebanking.FakeCoreBanking;
import java.io.IOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.ResultActions;

/** The Customers endpoints against a fake CBA-CORE, every answer checked against the contract. */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class CustomerEndpointsIT {

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

    private static RequestPostProcessor client(String clientId) {
        return jwt().jwt(jwt -> jwt.claim("azp", clientId)).authorities(new SimpleGrantedAuthority("SCOPE_customers"));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(request.with(client("app")).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String person(String bvn, String mobile) {
        return """
                {"firstName":"Ada","middleName":"Ngozi","lastName":"Obi","dateOfBirth":"1999-06-15",
                 "email":"ada.%s@example.com","mobileNumber":"%s","bvn":"%s","nin":"1%s",
                 "address":{"addressLine1":"12 Allen Avenue","city":"Ikeja","state":"Lagos"},
                 "productId":1,"kycLevel":1,"pep":false,"riskRating":"LOW"}"""
                .formatted(mobile, mobile, bvn, bvn.substring(1));
    }

    private long created(String body) throws Exception {
        return created("/v1/individual-customers", body);
    }

    private long business(String body) throws Exception {
        return created("/v1/business-customers", body);
    }

    private long created(String path, String body) throws Exception {
        String response = send(post(path), body).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        return JSON.readTree(response).path("clientId").asLong();
    }

    @Test
    void createsAPersonWithTheirFirstAccount(CapturedOutput output) throws Exception {
        send(post("/v1/individual-customers"), person("22200000101", "08000000101"))
                .andExpect(status().isCreated())
                .andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.accountNumber").value(org.hamcrest.Matchers.matchesPattern("\\d{10}")))
                .andExpect(jsonPath("$.address.state").value("Lagos"))
                .andExpect(jsonPath("$.subLedgerActivated").value(false));

        assertThat(output.getOut()).doesNotContain("22200000101").contains("*******0101");
    }

    @Test
    void returnsTheFirstResultWhenTheSameReferenceIsSentAgain() throws Exception {
        String body = person("22200000102", "08000000102").replace("\"kycLevel\":1",
                "\"kycLevel\":1,\"transactionReference\":\"REF-0102\"");
        long first = created(body);
        int customers = CORE.clientCount();

        assertThat(created(body)).isEqualTo(first);
        assertThat(CORE.clientCount()).isEqualTo(customers);
    }

    @Test
    void refusesABvnOrMobileNumberAlreadyInUse() throws Exception {
        created(person("22200000103", "08000000103"));

        send(post("/v1/individual-customers"), person("22200000103", "08000000999"))
                .andExpect(status().isConflict()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.code").value("CUSTOMER_EXISTS"))
                .andExpect(jsonPath("$.detail").value("A customer with this BVN already exists"));
        send(post("/v1/individual-customers"), person("22200000998", "08000000103"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A customer with this mobile number already exists"));
    }

    @Test
    void refusesATinAlreadyInUse() throws Exception {
        created(person("22200000130", "08000000130").replace("\"kycLevel\":1", "\"kycLevel\":1,\"tin\":\"11112222-0001\""));

        business("""
                {"businessName":"Tin Test Ltd","email":"tin@example.com","mobileNumber":"08000000131","bvn":"22200000131",
                 "kycLevel":1,"productId":1,"address":{"addressLine1":"1 Road","state":"Lagos"}}""");
        send(post("/v1/business-customers"), """
                {"businessName":"Tin Copy Ltd","email":"copy@example.com","mobileNumber":"08000000132","bvn":"22200000132",
                 "tin":"11112222-0001","kycLevel":1,"productId":1,"address":{"addressLine1":"1 Road","state":"Lagos"}}""")
                .andExpect(status().isConflict()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.detail").value("A customer with this TIN already exists"));
    }

    @Test
    void refusesInvalidCustomers() throws Exception {
        String valid = person("22200000104", "08000000104");
        expectInvalidWithoutContractCheck(valid.replace("\"lastName\":\"Obi\",", ""), "lastName");
        expectInvalidWithoutContractCheck(valid.replace("\"firstName\":\"Ada\",", ""), "firstName");
        expectInvalidWithoutContractCheck(valid.replace("\"kycLevel\":1", "\"kycLevel\":1,\"tin\":\"12AB\""), "tin");
        expectInvalid(valid.replace("\"state\":\"Lagos\"", "\"state\":\"Atlantis\""), "address.state");
        expectInvalid(valid.replace("\"kycLevel\":1", "\"kycLevel\":7"), "kycLevel");
        expectInvalid(valid.replace("\"productId\":1", "\"productId\":99"), "productId");
        // Not checked against the contract: the request itself breaks it.
        send(post("/v1/individual-customers"), valid.replace("\"bvn\":\"22200000104\"", "\"bvn\":\"123\""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("bvn"))
                .andExpect(jsonPath("$.errors[0].message").value("must be exactly 11 digits"));
    }

    /** For requests that themselves break the contract, which the contract check would refuse before the API. */
    private void expectInvalidWithoutContractCheck(String body, String field) throws Exception {
        send(post("/v1/individual-customers"), body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value(field));
    }

    private void expectInvalid(String body, String field) throws Exception {
        send(post("/v1/individual-customers"), body).andExpect(status().isBadRequest()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value(field));
    }

    @Test
    void createsABusinessWithItsDirectorsAndReadsItBack() throws Exception {
        long clientId = business("""
                {"businessName":"Ada Ventures Ltd","registrationDate":"2019-03-01","email":"hello@adaventures.example",
                 "mobileNumber":"08000000105","bvn":"22200000105","cacNumber":"RC123456","tin":"12345678-0001",
                 "kycLevel":2,"productId":1,
                 "address":{"addressLine1":"5 Broad Street","state":"Lagos","country":"Nigeria"},
                 "transactionLimit":"500000",
                 "relatedPersons":[{"firstName":"Ada","lastName":"Obi","role":"DIRECTOR_AND_SIGNATORY","bvn":"22200000101",
                                    "ownershipPercentage":60},
                                   {"firstName":"Bayo","lastName":"Ade","role":"SIGNATORY"}]}""");

        mvc.perform(get("/v1/customers/{id}", clientId).with(client("app")))
                .andExpect(status().isOk()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.clientType").value("BUSINESS"))
                .andExpect(jsonPath("$.businessName").value("Ada Ventures Ltd"))
                .andExpect(jsonPath("$.registrationDate").value("2019-03-01"))
                .andExpect(jsonPath("$.dateOfBirth").doesNotExist())
                .andExpect(jsonPath("$.firstName").doesNotExist())
                .andExpect(jsonPath("$.cacNumber").value("RC123456"))
                .andExpect(jsonPath("$.tin").value("12345678-0001"))
                .andExpect(jsonPath("$.kycLevel").value(2))
                .andExpect(jsonPath("$.transactionLimit").value("500000.00"))
                .andExpect(jsonPath("$.relatedPersons.length()").value(2))
                .andExpect(jsonPath("$.relatedPersons[0].role").value("DIRECTOR_AND_SIGNATORY"))
                .andExpect(jsonPath("$.relatedPersons[1].role").value("SIGNATORY"));
    }

    @Test
    void readsAndChangesAPersonsKycRiskAndLimit() throws Exception {
        long clientId = created(person("22200000106", "08000000106"));

        mvc.perform(get("/v1/customers/{id}", clientId).with(client("app")))
                .andExpect(status().isOk()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.dateOfBirth").value("1999-06-15"))
                .andExpect(jsonPath("$.address.country").value("Nigeria"))
                .andExpect(jsonPath("$.kycLevel").value(1))
                .andExpect(jsonPath("$.riskRating").value("LOW"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        send(put("/v1/customers/{id}/kyc-level", clientId), "{\"kycLevel\":3}")
                .andExpect(status().isOk()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.kycLevel").value(3));
        send(put("/v1/customers/{id}/kyc-level", clientId), "{\"kycLevel\":2,\"clientId\":1}")
                .andExpect(status().isBadRequest());
        send(put("/v1/customers/{id}/risk-profile", clientId), "{\"pep\":true}")
                .andExpect(status().isOk()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.pep").value(true))
                .andExpect(jsonPath("$.riskRating").value("LOW"));
        send(put("/v1/customers/{id}/risk-profile", clientId), "{}").andExpect(status().isBadRequest());
        send(put("/v1/customers/{id}/transaction-limit", clientId), "{\"transactionLimit\":\"250000.5\"}")
                .andExpect(status().isOk()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.transactionLimit").value("250000.50"));
        send(put("/v1/customers/{id}/transaction-limit", clientId), "{\"transactionLimit\":\"0\"}")
                .andExpect(status().isBadRequest());

        mvc.perform(get("/v1/customers/{id}", clientId).with(client("app")))
                .andExpect(jsonPath("$.kycLevel").value(3)).andExpect(jsonPath("$.pep").value(true))
                .andExpect(jsonPath("$.transactionLimit").value("250000.50"));
    }

    @Test
    void updatesACustomerAllOrNothing() throws Exception {
        long a = created(person("22200000120", "08000000120"));
        long b = created(person("22200000121", "08000000121"));

        send(put("/v1/individual-customers/{id}", a), """
                {"mobileNumber":"08000000122","nin":"13300000220","lastName":"Obi-Eze",
                 "address":{"addressLine1":"5 Broad Street","state":"Lagos"}}""")
                .andExpect(status().isOk()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.clientId").value(a))
                .andExpect(jsonPath("$.mobileNumber").value("08000000122"))
                .andExpect(jsonPath("$.lastName").value("Obi-Eze"))
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.address.addressLine1").value("5 Broad Street"))
                .andExpect(jsonPath("$.address.city").doesNotExist());

        // B's new phone number comes with A's NIN: refused, and B's phone number is not changed either
        send(put("/v1/individual-customers/{id}", b), "{\"mobileNumber\":\"08000000123\",\"nin\":\"13300000220\"}")
                .andExpect(status().isConflict()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.code").value("CUSTOMER_EXISTS"))
                .andExpect(jsonPath("$.detail").value("A customer with this NIN already exists"));
        mvc.perform(get("/v1/customers/{id}", b).with(client("app")))
                .andExpect(jsonPath("$.mobileNumber").value("08000000121"));

        send(put("/v1/individual-customers/{id}", b), "{\"mobileNumber\":\"08000000122\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A customer with this mobile number already exists"));
        // A name changes as a whole in the core ledger; sending one part keeps the others
        send(put("/v1/individual-customers/{id}", b), "{\"middleName\":\"Chioma\"}")
                .andExpect(status().isOk()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.firstName").value("Ada")).andExpect(jsonPath("$.middleName").value("Chioma"))
                .andExpect(jsonPath("$.lastName").value("Obi"));
    }

    @Test
    void replacesABusinessesDirectors() throws Exception {
        long clientId = business("""
                {"businessName":"Bayo Stores Ltd","email":"hello@bayostores.example","mobileNumber":"08000000124",
                 "bvn":"22200000124","cacNumber":"RC654321","kycLevel":2,"productId":1,
                 "address":{"addressLine1":"9 Marina","state":"Lagos"},
                 "relatedPersons":[{"firstName":"Ada","lastName":"Obi","role":"DIRECTOR"},
                                   {"firstName":"Bayo","lastName":"Ade","role":"SIGNATORY"}]}""");

        send(put("/v1/business-customers/{id}", clientId), """
                {"businessName":"Bayo Stores Nigeria Ltd","tin":"98765432-0001",
                 "relatedPersons":[{"firstName":"Chidi","lastName":"Okafor","role":"DIRECTOR_AND_SIGNATORY"}]}""")
                .andExpect(status().isOk()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.businessName").value("Bayo Stores Nigeria Ltd"))
                .andExpect(jsonPath("$.tin").value("98765432-0001"))
                .andExpect(jsonPath("$.relatedPersons.length()").value(1))
                .andExpect(jsonPath("$.relatedPersons[0].firstName").value("Chidi"));
    }

    @Test
    void refusesUpdatesThatDoNotFitTheCustomer() throws Exception {
        long person = created(person("22200000125", "08000000125"));
        int before = CORE.clientCount();

        send(put("/v1/individual-customers/{id}", person), "{}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        send(put("/v1/business-customers/{id}", person), "{\"businessName\":\"Ada Ltd\"}")
                .andExpect(status().isBadRequest()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.errors[0].field").value("clientId"))
                .andExpect(jsonPath("$.detail").value("Customer " + person
                        + " is an individual; use PUT /v1/individual-customers/" + person));
        send(put("/v1/individual-customers/{id}", person), "{\"firstName\":\" \"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("firstName"));
        send(put("/v1/individual-customers/{id}", person), "{\"address\":{\"addressLine1\":\"x\",\"state\":\"Atlantis\"}}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("address.state"));

        CORE.setOwner(person, "irecharge");
        send(put("/v1/individual-customers/{id}", person), "{\"firstName\":\"Eve\"}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
        assertThat(CORE.clientCount()).isEqualTo(before);
    }

    @Test
    void hidesOtherClientsCustomers() throws Exception {
        long clientId = created(person("22200000107", "08000000107"));
        CORE.setOwner(clientId, "irecharge");

        mvc.perform(get("/v1/customers/{id}", clientId).with(client("app")))
                .andExpect(status().isNotFound()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
        send(put("/v1/customers/{id}/kyc-level", clientId), "{\"kycLevel\":3}").andExpect(status().isNotFound());
        send(post("/v1/customers/search"), "{\"bvn\":\"22200000107\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/v1/customers/{id}", 999_999).with(client("app"))).andExpect(status().isNotFound());
    }

    @Test
    void searchesByBvnEmailOrPhonePageByPage() throws Exception {
        long first = created(person("22200000108", "08000000108"));
        created(person("22200000109", "08000000109").replace("ada.08000000109@", "shared@"));
        created(person("22200000110", "08000000110").replace("ada.08000000110@", "shared@"));

        send(post("/v1/customers/search"), "{\"bvn\":\"22200000108\"}")
                .andExpect(status().isOk()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.items[0].clientId").value(first))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
        send(post("/v1/customers/search"), "{\"mobileNumber\":\"08000000108\"}")
                .andExpect(jsonPath("$.items.length()").value(1));

        String page1 = send(post("/v1/customers/search?limit=1"), "{\"email\":\"SHARED@example.com\"}")
                .andExpect(status().isOk()).andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        JsonNode firstPage = JSON.readTree(page1);
        String cursor = firstPage.path("nextCursor").asText();
        assertThat(cursor).isNotBlank();
        send(post("/v1/customers/search?limit=1&cursor=" + cursor), "{\"email\":\"shared@example.com\"}")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].clientId").value(org.hamcrest.Matchers.not(
                        firstPage.path("items").get(0).path("clientId").asLong())))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());

        send(post("/v1/customers/search"), "{}").andExpect(status().isBadRequest());
        send(post("/v1/customers/search?cursor=not-a-cursor"), "{\"bvn\":\"22200000108\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("cursor"));
        send(post("/v1/customers/search?limit=500"), "{\"bvn\":\"22200000108\"}").andExpect(status().isBadRequest());
    }

    @Test
    void reportsTheCoreLedgerBeingUnreachable() throws Exception {
        long clientId = created(person("22200000111", "08000000111"));
        CORE.setDown(true);

        mvc.perform(get("/v1/customers/{id}", clientId).with(client("app")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UPSTREAM_UNAVAILABLE"));
    }

    @Test
    void refusesClientsWithoutTheCustomersScope() throws Exception {
        mvc.perform(get("/v1/customers/1").with(jwt().jwt(jwt -> jwt.claim("azp", "irecharge"))
                .authorities(new SimpleGrantedAuthority("SCOPE_accounts"))))
                .andExpect(status().isForbidden());
    }
}
