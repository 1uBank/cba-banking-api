package com.oneutility.cba.bankingapi.corebanking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oneutility.cba.bankingapi.common.logging.OutboundLoggingInterceptor;
import com.oneutility.cba.bankingapi.common.web.CorrelationId;
import com.oneutility.cba.bankingapi.corebanking.fineract.FineractError;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * The HTTP client for CBA-CORE. Every call:
 * <ul>
 * <li>carries the Banking API's token, the tenant and the caller's correlation ID;</li>
 * <li>is logged with masked bodies ({@link OutboundLoggingInterceptor}, peer {@code cba-core});</li>
 * <li>is tried once more with a new token if CBA-CORE answers 401 (the request was not processed);</li>
 * <li>fails with {@link CoreBankingException}: refused (with CBA-CORE's error codes), unavailable or timed out.</li>
 * </ul>
 * Nothing else is retried: a call that moves money is only ever resent with the same {@code Idempotency-Key}.
 */
@Configuration(proxyBeanMethods = false)
class CoreBankingConfiguration {

    static final String TENANT_HEADER = "Fineract-Platform-TenantId";

    @Bean
    CoreBankingTokens coreBankingTokens(RestClient.Builder builder, CoreBankingProperties properties) {
        RestClient http = builder.clone().requestFactory(requestFactory(properties))
                .requestInterceptor(new OutboundLoggingInterceptor("auth-service")).build();
        return new CoreBankingTokens(http, properties, Clock.systemUTC());
    }

    @Bean
    FineractApi fineractApi(RestClient.Builder builder, CoreBankingProperties properties, CoreBankingTokens tokens,
            ObjectMapper objectMapper) {
        RestClient http = builder.clone().baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory(properties))
                .requestInterceptors(interceptors -> {
                    interceptors.add(new FailureInterceptor());
                    interceptors.add(new SignInInterceptor(tokens, properties.tenantId()));
                    interceptors.add(new OutboundLoggingInterceptor("cba-core"));
                })
                .defaultStatusHandler(HttpStatusCode::isError,
                        (request, response) -> {
                            throw refused(response, objectMapper);
                        })
                .build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(http)).build().createClient(FineractApi.class);
    }

    private static ClientHttpRequestFactory requestFactory(CoreBankingProperties properties) {
        ClientHttpRequestFactorySettings timeouts = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(properties.connectTimeout()).withReadTimeout(properties.readTimeout());
        return new BufferingClientHttpRequestFactory(ClientHttpRequestFactoryBuilder.detect().build(timeouts));
    }

    static CoreBankingException refused(ClientHttpResponse response, ObjectMapper objectMapper) throws IOException {
        int status = response.getStatusCode().value();
        List<String> codes = new ArrayList<>();
        String message = "CBA-CORE answered " + status;
        byte[] body = StreamUtils.copyToByteArray(response.getBody());
        try {
            FineractError error = objectMapper.readValue(body, FineractError.class);
            addCode(codes, error.userMessageGlobalisationCode());
            String detail = error.defaultUserMessage();
            for (FineractError.Detail field : error.errors()) {
                addCode(codes, field.userMessageGlobalisationCode());
                if (field.defaultUserMessage() != null) {
                    detail = field.defaultUserMessage();
                }
            }
            if (detail != null) {
                message = message + ": " + detail;
            }
        } catch (IOException notFineractError) {
            // Not a Fineract error body (for example a proxy's page); the status says enough.
        }
        return new CoreBankingException(CoreBankingException.Kind.REFUSED, status, codes, message);
    }

    private static void addCode(List<String> codes, String code) {
        if (code != null && !code.isBlank() && !codes.contains(code)) {
            codes.add(code);
        }
    }

    /** Adds the token, tenant and correlation ID; on 401, gets a new token and tries once more. */
    static final class SignInInterceptor implements ClientHttpRequestInterceptor {

        private final CoreBankingTokens tokens;
        private final String tenantId;

        SignInInterceptor(CoreBankingTokens tokens, String tenantId) {
            this.tokens = tokens;
            this.tenantId = tenantId;
        }

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
                throws IOException {
            String token = tokens.current();
            prepare(request, token);
            ClientHttpResponse response = execution.execute(request, body);
            if (response.getStatusCode().value() != 401) {
                return response;
            }
            response.close();
            tokens.invalidate(token);
            prepare(request, tokens.current());
            return execution.execute(request, body);
        }

        private void prepare(HttpRequest request, String token) {
            HttpHeaders headers = request.getHeaders();
            headers.setBearerAuth(token);
            headers.set(TENANT_HEADER, tenantId);
            String correlationId = CorrelationId.current();
            if (correlationId != null) {
                headers.set(CorrelationId.HEADER, correlationId);
            }
        }
    }

    /** Turns "no answer" into {@link CoreBankingException}, telling a timeout (outcome unknown) from a refusal. */
    static final class FailureInterceptor implements ClientHttpRequestInterceptor {

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) {
            try {
                return execution.execute(request, body);
            } catch (SocketTimeoutException | HttpTimeoutException e) {
                throw CoreBankingException.timeout("CBA-CORE did not answer " + request.getMethod() + " "
                        + request.getURI().getPath() + " in time");
            } catch (IOException e) {
                throw CoreBankingException.unavailable("CBA-CORE could not be reached: " + e.getMessage());
            }
        }
    }
}
