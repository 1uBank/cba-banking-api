package com.oneutility.cba.bankingapi.common.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * Writes one log line per HTTP call: calls to the Banking API ({@code inbound}) and its calls to other systems
 * ({@code outbound}, named by {@code peer}). Bodies must already be masked with {@link SensitiveData}. The correlation
 * ID comes from the log context, so a client's call and the calls it caused share it.
 */
public final class HttpCallLog {

    private static final Logger INBOUND = LoggerFactory.getLogger("http.inbound");
    private static final Logger OUTBOUND = LoggerFactory.getLogger("http.outbound");

    private HttpCallLog() {
    }

    /**
     * @param path the path and query string, masked
     * @param clientId the calling client, or {@code null} when it was not authenticated
     */
    public static void inbound(String method, String path, int status, long durationMs, String clientId,
            String requestBody, String responseBody) {
        LoggingEventBuilder event = INBOUND.atLevel(status >= 500 ? Level.WARN : Level.INFO)
                .addKeyValue("http.direction", "inbound").addKeyValue("http.request.method", method)
                .addKeyValue("url.path", path).addKeyValue("http.response.status_code", status)
                .addKeyValue("event.duration_ms", durationMs);
        if (clientId != null) {
            event = event.addKeyValue("client.id", clientId);
        }
        event.log("{} {} -> {} in {} ms, client {}, request {}, response {}", method, path, status, durationMs,
                clientId == null ? "-" : clientId, orDash(requestBody), orDash(responseBody));
    }

    /**
     * @param peer the system called, for example {@code cba-core}
     * @param url the URL called, query string masked
     * @param status the response status, or {@code null} when no response came back
     * @param failure why no response came back, or {@code null}
     */
    public static void outbound(String peer, String method, String url, Integer status, long durationMs,
            String requestBody, String responseBody, String failure) {
        boolean failed = status == null || status >= 500;
        LoggingEventBuilder event = OUTBOUND.atLevel(failed ? Level.WARN : Level.INFO)
                .addKeyValue("http.direction", "outbound").addKeyValue("service.target.name", peer)
                .addKeyValue("http.request.method", method).addKeyValue("url.full", url)
                .addKeyValue("event.duration_ms", durationMs);
        if (status != null) {
            event = event.addKeyValue("http.response.status_code", status);
        }
        if (failure != null) {
            event = event.addKeyValue("error.message", failure);
        }
        event.log("{} {} {} -> {} in {} ms, request {}, response {}", peer, method, url,
                status == null ? "no response (" + failure + ")" : status, durationMs, orDash(requestBody),
                orDash(responseBody));
    }

    private static String orDash(String body) {
        return body == null ? "-" : body;
    }
}
