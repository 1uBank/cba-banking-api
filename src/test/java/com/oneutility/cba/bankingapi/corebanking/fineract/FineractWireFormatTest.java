package com.oneutility.cba.bankingapi.corebanking.fineract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/** The JSON the Banking API sends CBA-CORE, and CBA-CORE's real answers (shapes taken from a running instance). */
class FineractWireFormatTest {

    private final ObjectMapper json = Jackson2ObjectMapperBuilder.json().build();

    @Test
    void anUpdateSendsOnlyWhatChanges() throws Exception {
        UpdateCustomerCommand command = new UpdateCustomerCommand(
                new ClientChanges(null, null, null, null, null, null, "08011112222", FineractLocale.ENGLISH,
                        FineractLocale.DATE_FORMAT),
                List.of(IdentifierFields.active(17, "12345678901")), null, null);

        assertThat(json.writeValueAsString(command)).isEqualTo("""
                {"client":{"mobileNo":"08011112222","locale":"en","dateFormat":"yyyy-MM-dd"},\
                "identifiers":[{"documentTypeId":17,"documentKey":"12345678901","status":"Active"}]}""");
    }

    @Test
    void datatableRowsUseTheColumnNamesAndPlainValues() throws Exception {
        assertThat(json.writeValueAsString(KycRow.of(41))).isEqualTo("{\"KycLevel_cd_KYC Level\":41,\"locale\":\"en\"}");
        assertThat(json.writeValueAsString(LimitRow.of(new BigDecimal("2.5E+5"))))
                .isEqualTo("{\"Transaction Limit\":\"250000\",\"locale\":\"en\"}");
        assertThat(json.writeValueAsString(RiskRow.of(true, null))).isEqualTo("{\"PEP\":true,\"locale\":\"en\"}");
        assertThat(json.writeValueAsString(RelatedPersonRow.of("Ada", null, "Obi", 7, null, null,
                LocalDate.of(1980, 2, 1), null, null, null, new BigDecimal("60"))))
                .isEqualTo("""
                        {"First Name":"Ada","Last Name":"Obi","RelatedPersonRole_cd_Role":7,"Date of Birth":"1980-02-01",\
                        "Ownership Percentage":"60","locale":"en","dateFormat":"yyyy-MM-dd"}""");
    }

    @Test
    void readsAClientWithFineractsDateArrays() throws Exception {
        ClientResponse client = json.readValue("""
                {"id":12,"accountNo":"000000012","status":{"id":300,"code":"clientStatusType.active","value":"Active"},
                 "active":true,"activationDate":[2026,11,23],"firstname":"Ada","lastname":"Obi","dateOfBirth":[1999,6,15],
                 "mobileNo":"08012345678","legalForm":{"id":1,"code":"legalFormType.person","value":"PERSON"},
                 "timeline":{"submittedOnDate":[2026,11,23]},"groups":[]}""", ClientResponse.class);

        assertThat(client.dateOfBirth()).isEqualTo(LocalDate.of(1999, 6, 15));
        assertThat(client.legalForm().id()).isEqualTo(1L);
        assertThat(client.status().code()).isEqualTo("clientStatusType.active");
    }

    @Test
    void readsDatatableRows() throws Exception {
        List<RelatedPersonRow> rows = json.readValue("""
                [{"id":3,"client_id":12,"First Name":"Ada","Middle Name":null,"Last Name":"Obi",
                  "RelatedPersonRole_cd_Role":7,"BVN":null,"NIN":null,"Date of Birth":[1980,2,1],"Phone":null,
                  "Email":null,"Address":null,"Ownership Percentage":60.000000,
                  "created_at":[2026,11,23,8,1,2],"updated_at":[2026,11,23,8,1,2]}]""", new TypeReference<>() {
        });
        KycRow kyc = json.readValue("{\"client_id\":12,\"KycLevel_cd_KYC Level\":41,\"created_at\":[2026,11,23]}",
                KycRow.class);

        assertThat(rows.get(0).roleId()).isEqualTo(7L);
        assertThat(rows.get(0).dateOfBirth()).isEqualTo(LocalDate.of(1980, 2, 1));
        assertThat(rows.get(0).ownershipPercentage()).isEqualByComparingTo("60");
        assertThat(kyc.kycLevelId()).isEqualTo(41L);
    }

    @Test
    void readsASavingsAccountAndItsBalances() throws Exception {
        SavingsAccountResponse account = json.readValue("""
                {"id":15,"accountNo":"0770000011","clientId":12,"savingsProductId":3,"savingsProductName":"Savings",
                 "status":{"id":300,"code":"savingsAccountStatusType.active","active":true,"closed":false},
                 "subStatus":{"id":0,"code":"SavingsAccountSubStatusEnum.none","dormant":false,"block":false,
                              "blockCredit":false,"blockDebit":false},
                 "currency":{"code":"NGN","decimalPlaces":2},
                 "summary":{"accountBalance":1500.50,"availableBalance":1000.00,"totalInterestPosted":0},
                 "timeline":{"activatedOnDate":[2026,11,23]}}""", SavingsAccountResponse.class);

        assertThat(account.accountNo()).isEqualTo("0770000011");
        assertThat(account.status().active()).isTrue();
        assertThat(account.summary().availableBalance()).isEqualByComparingTo("1000");
    }

    @Test
    void readsFineractErrors() throws Exception {
        FineractError error = json.readValue("""
                {"developerMessage":"...","httpStatusCode":"403","defaultUserMessage":"Errors contain reason for domain rule violation.",
                 "userMessageGlobalisationCode":"validation.msg.domain.rule.violation",
                 "errors":[{"userMessageGlobalisationCode":"error.msg.clientIdentifier.identityKey.duplicate",
                            "defaultUserMessage":"Client identifier of type NIN with value of 12345678901 already exists.",
                            "parameterName":"id","args":[]}]}""", FineractError.class);

        assertThat(error.errors()).extracting(FineractError.Detail::userMessageGlobalisationCode)
                .containsExactly("error.msg.clientIdentifier.identityKey.duplicate");
    }
}
