package com.oneutility.cba.bankingapi.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class SensitiveDataTest {

    private static String json(String body) {
        return SensitiveData.body(body.getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_JSON);
    }

    @Test
    void keepsOnlyTheLastFourDigitsOfIdentityNumbersWherever_theyAre() {
        String logged = json("""
                {"bvn":"22200000001","nin":"12345678901","relatedPersons":[{"BVN":"22211112222"}],
                 "identifiers":[{"documentKey":"22200000001"}]}""");

        assertThat(logged).doesNotContain("22200000001", "12345678901", "22211112222")
                .contains("\"bvn\":\"*******0001\"", "\"nin\":\"*******8901\"", "\"BVN\":\"*******2222\"",
                        "\"documentKey\":\"*******0001\"");
    }

    @Test
    void masksIdentityNumbersQuotedInCoreBankingErrorMessages() {
        String logged = json("""
                {"errors":[{"developerMessage":"Client identifier of type BVN with value of 22200000001 already exists.",
                 "defaultUserMessage":"Client identifier of type NIN with value of 12345678901 already exists."}]}""");

        assertThat(logged).doesNotContain("22200000001", "12345678901")
                .contains("type BVN with value of *******0001 already exists.",
                        "type NIN with value of *******8901 already exists.");
    }

    @Test
    void neverLogsSecrets() {
        String logged = json("""
                {"access_token":"eyJhbGciOi.payload.sig","client_secret":"s3cr3t","password":"p","pin":"1234",
                 "nested":{"refreshToken":"r"}}""");

        assertThat(logged).doesNotContain("eyJhbGciOi", "s3cr3t", "\"p\"", "1234", "\"r\"")
                .contains("\"access_token\":\"[REDACTED]\"", "\"client_secret\":\"[REDACTED]\"");
    }

    @Test
    void logsCustomerAndAccountDetailsAsSent() {
        String body = """
                {"accountNumber":"2000000158","firstName":"Ada","lastName":"Obi","email":"ada.obi@example.com",\
                "mobileNumber":"08012345678","dateOfBirth":"1999-06-15","address":{"addressLine1":"12 Allen Avenue"}}""";

        assertThat(json(body)).isEqualTo(body);
    }

    @Test
    void masksFormBodiesAndQueryStrings() {
        assertThat(SensitiveData.body("grant_type=client_credentials&client_secret=abc%2Fdef&scope=customers"
                .getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_FORM_URLENCODED))
                .isEqualTo("grant_type=client_credentials&client_secret=%5BREDACTED%5D&scope=customers");
        assertThat(SensitiveData.form("bvn=22200000001&limit=20")).isEqualTo("bvn=*******0001&limit=20");
    }

    @Test
    void doesNotLogFilesOrInvalidJson() {
        assertThat(SensitiveData.body(new byte[] { 1, 2, 3 }, MediaType.IMAGE_PNG)).isEqualTo(
                "[3 bytes of image/png not logged]");
        assertThat(json("{\"bvn\":\"22200000001\"")).doesNotContain("22200000001");
        assertThat(SensitiveData.body(new byte[0], MediaType.APPLICATION_JSON)).isNull();
    }

    @Test
    void cutsOffLongBodies() {
        String logged = json("{\"note\":\"" + "x".repeat(20_000) + "\"}");

        assertThat(logged).hasSizeLessThan(SensitiveData.MAX_BODY_CHARS + 50).endsWith("more]");
    }

    @Test
    void secretHeadersAreRecognised() {
        assertThat(SensitiveData.isSecretHeader("Authorization")).isTrue();
        assertThat(SensitiveData.isSecretHeader("X-Correlation-Id")).isFalse();
    }
}
