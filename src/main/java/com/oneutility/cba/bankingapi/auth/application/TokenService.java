package com.oneutility.cba.bankingapi.auth.application;

import com.oneutility.cba.bankingapi.shared.security.ActiveClientCheck;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Issues access tokens. Reads the client's credentials from the form or from HTTP Basic authentication, refuses a
 * client that is not active, and forwards the request to the auth service, which checks the secret and signs the
 * token.
 */
@Service
public class TokenService {

    private static final Logger LOG = LoggerFactory.getLogger(TokenService.class);
    private static final String CLIENT_CREDENTIALS = "client_credentials";
    private static final String BASIC = "Basic ";

    private final ActiveClientCheck activeClients;
    private final AuthServiceTokenGateway authService;

    public TokenService(ActiveClientCheck activeClients, AuthServiceTokenGateway authService) {
        this.activeClients = activeClients;
        this.authService = authService;
    }

    /**
     * @param grantType the form's {@code grant_type}
     * @param formClientId the form's {@code client_id}, if sent
     * @param formClientSecret the form's {@code client_secret}, if sent
     * @param authorization the {@code Authorization} header, if sent
     * @param scope the form's {@code scope}, if sent
     */
    public IssuedToken issue(String grantType, String formClientId, String formClientSecret, String authorization,
            String scope) {
        if (grantType == null || grantType.isBlank()) {
            throw new TokenRequestException(HttpStatus.BAD_REQUEST, "invalid_request", "grant_type is required");
        }
        if (!CLIENT_CREDENTIALS.equals(grantType)) {
            throw new TokenRequestException(HttpStatus.BAD_REQUEST, "unsupported_grant_type",
                    "Only client_credentials is supported");
        }
        ClientCredentials credentials = credentials(formClientId, formClientSecret, authorization);
        if (!activeClients.isActive(credentials.clientId())) {
            LOG.warn("Token refused for client {}: not an active client", credentials.clientId());
            throw TokenRequestException.invalidClient();
        }
        IssuedToken token = authService.requestToken(credentials, blankToNull(scope));
        LOG.info("Token issued to client {} for scopes [{}]", credentials.clientId(), token.scope());
        return token;
    }

    private static ClientCredentials credentials(String formClientId, String formClientSecret, String authorization) {
        boolean inHeader = authorization != null && authorization.regionMatches(true, 0, BASIC, 0, BASIC.length());
        boolean inForm = formClientSecret != null && !formClientSecret.isBlank();
        if (inHeader && inForm) {
            // RFC 6749 section 2.3: a client uses one authentication method per request.
            throw new TokenRequestException(HttpStatus.BAD_REQUEST, "invalid_request",
                    "Send the client credentials either in the form or as HTTP Basic authentication, not both");
        }
        if (inHeader) {
            return fromBasic(authorization.substring(BASIC.length()).trim());
        }
        if (formClientId == null || formClientId.isBlank() || !inForm) {
            throw TokenRequestException.invalidClient();
        }
        return new ClientCredentials(formClientId, formClientSecret);
    }

    private static ClientCredentials fromBasic(String encoded) {
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw TokenRequestException.invalidClient();
        }
        int colon = decoded.indexOf(':');
        if (colon <= 0 || colon == decoded.length() - 1) {
            throw TokenRequestException.invalidClient();
        }
        // RFC 6749 section 2.3.1: both parts are form-encoded before Base64.
        return new ClientCredentials(formDecode(decoded.substring(0, colon)), formDecode(decoded.substring(colon + 1)));
    }

    private static String formDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
