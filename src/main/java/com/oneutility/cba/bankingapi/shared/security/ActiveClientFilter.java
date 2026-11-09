package com.oneutility.cba.bankingapi.shared.security;

import com.oneutility.cba.bankingapi.shared.error.ErrorCode;
import com.oneutility.cba.bankingapi.shared.error.ProblemWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses a valid token whose client has since been suspended, so suspension takes effect without waiting for the
 * client's tokens to expire.
 */
class ActiveClientFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(ActiveClientFilter.class);

    private final ActiveClientCheck activeClients;
    private final ProblemWriter problems;

    ActiveClientFilter(ActiveClientCheck activeClients, ProblemWriter problems) {
        this.activeClients = activeClients;
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<String> clientId = CurrentClient.find();
        if (clientId.isPresent() && !activeClients.isActive(clientId.get())) {
            LOG.warn("Refused a call from client {}: not an active client", clientId.get());
            SecurityContextHolder.clearContext();
            problems.write(request, response, ErrorCode.UNAUTHORIZED, "The client is not active");
            return;
        }
        chain.doFilter(request, response);
    }
}
