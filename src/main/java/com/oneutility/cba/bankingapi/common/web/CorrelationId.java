package com.oneutility.cba.bankingapi.common.web;

import org.slf4j.MDC;

/** The current request's correlation ID, as set by {@link CorrelationIdFilter}. */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {
    }

    /** The correlation ID of the request on this thread, or {@code null} outside a request. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
