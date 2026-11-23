package com.oneutility.cba.bankingapi.common.logging;

import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

/**
 * Logs every call a {@code RestClient} makes to another system, with masked bodies. The client's request factory must
 * buffer responses ({@code BufferingClientHttpRequestFactory}) so the body can be read here and again by the caller.
 */
public class OutboundLoggingInterceptor implements ClientHttpRequestInterceptor {

    private final String peer;

    /** @param peer the system called, as it appears in the logs, for example {@code cba-core} */
    public OutboundLoggingInterceptor(String peer) {
        this.peer = peer;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        long start = System.nanoTime();
        String url = maskedUrl(request.getURI());
        String requestBody = SensitiveData.body(body, request.getHeaders().getContentType());
        ClientHttpResponse response;
        try {
            response = execution.execute(request, body);
        } catch (IOException | RuntimeException e) {
            HttpCallLog.outbound(peer, request.getMethod().name(), url, null, millisSince(start), requestBody, null,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;
        }
        byte[] responseBody = StreamUtils.copyToByteArray(response.getBody());
        MediaType responseType = response.getHeaders().getContentType();
        HttpCallLog.outbound(peer, request.getMethod().name(), url, response.getStatusCode().value(),
                millisSince(start), requestBody, SensitiveData.body(responseBody, responseType), null);
        return response;
    }

    static String maskedUrl(URI uri) {
        String query = uri.getRawQuery();
        String base = uri.getScheme() + "://" + uri.getRawAuthority() + uri.getRawPath();
        return query == null ? base : base + "?" + SensitiveData.form(query);
    }

    private static long millisSince(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
