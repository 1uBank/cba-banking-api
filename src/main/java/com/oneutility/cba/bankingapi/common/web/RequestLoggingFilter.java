package com.oneutility.cba.bankingapi.common.web;

import com.oneutility.cba.bankingapi.common.logging.HttpCallLog;
import com.oneutility.cba.bankingapi.common.logging.SensitiveData;
import com.oneutility.cba.bankingapi.common.security.CurrentClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Logs every call to the API once it has been answered: method, path, status, time taken, calling client, and the
 * request and response bodies with {@link SensitiveData} masking. Runs right after {@link CorrelationIdFilter}, so
 * rejected calls are logged too. The API reference under {@code /docs/} is not logged.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter implements WebMvcConfigurer {

    static final String CLIENT_ATTRIBUTE = RequestLoggingFilter.class.getName() + ".client";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/docs/") || path.equals("/docs") || path.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        ContentCachingRequestWrapper cachingRequest = new ContentCachingRequestWrapper(request,
                SensitiveData.MAX_BODY_CHARS * 4);
        ContentCachingResponseWrapper cachingResponse = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(cachingRequest, cachingResponse);
        } finally {
            String query = request.getQueryString();
            String path = request.getRequestURI() + (query == null ? "" : "?" + SensitiveData.form(query));
            HttpCallLog.inbound(request.getMethod(), path, cachingResponse.getStatus(),
                    (System.nanoTime() - start) / 1_000_000, (String) request.getAttribute(CLIENT_ATTRIBUTE),
                    SensitiveData.body(cachingRequest.getContentAsByteArray(), type(request.getContentType())),
                    SensitiveData.body(cachingResponse.getContentAsByteArray(), type(cachingResponse.getContentType())));
            cachingResponse.copyBodyToResponse();
        }
    }

    /** Records the authenticated client on the request, since the security context is gone by the time it is logged. */
    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                    @NonNull Object handler) {
                CurrentClient.find().ifPresent(id -> request.setAttribute(CLIENT_ATTRIBUTE, id));
                return true;
            }
        });
    }

    private static MediaType type(String contentType) {
        try {
            return contentType == null ? null : MediaType.parseMediaType(contentType);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
