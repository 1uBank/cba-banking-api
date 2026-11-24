package com.oneutility.cba.bankingapi.corebanking.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * How the Banking API reaches CBA-CORE. It signs in as its own service client in the auth service's {@code cba} realm
 * (client credentials); CBA-CORE maps that token to its user {@code service-account-banking-api}.
 *
 * @param baseUrl CBA-CORE's API, ending in {@code /fineract-provider/api/v1}
 * @param tenantId the CBA-CORE tenant, sent as {@code Fineract-Platform-TenantId}
 * @param tokenUrl the {@code cba} realm's token endpoint
 * @param clientId the Banking API's client in the {@code cba} realm
 * @param clientSecret its secret
 * @param officeId the office new customers are created in
 * @param connectTimeout connection timeout for calls to CBA-CORE and the token endpoint
 * @param readTimeout response timeout for calls to CBA-CORE
 * @param clientPlacedBy other CBA-CORE users whose holds and blocks count as placed by API clients
 */
@Validated
@ConfigurationProperties(prefix = "banking-api.core-banking")
public record CoreBankingProperties(@NotNull URI baseUrl, @NotBlank String tenantId, @NotNull URI tokenUrl,
        @NotBlank String clientId, @NotBlank String clientSecret, @NotNull @Positive Long officeId,
        @NotNull Duration connectTimeout, @NotNull Duration readTimeout, List<String> clientPlacedBy) {

    public CoreBankingProperties {
        clientPlacedBy = clientPlacedBy == null ? List.of() : List.copyOf(clientPlacedBy);
    }

    /**
     * Whether a hold or block made by this CBA-CORE user counts as placed by an API client, which can then lift or
     * release it: the Banking API's own service account, or one of {@code clientPlacedBy} (for example the user a
     * data migration loads TransactionPoint's API holds as).
     */
    public boolean placedByClient(String username) {
        return username != null && (username.equals("service-account-" + clientId) || clientPlacedBy.contains(username));
    }
}
