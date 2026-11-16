package com.oneutility.cba.bankingapi.auth.controller;

import com.oneutility.cba.bankingapi.auth.dto.TokenErrorResponse;
import com.oneutility.cba.bankingapi.auth.dto.TokenResponse;
import com.oneutility.cba.bankingapi.auth.service.TokenRequestException;
import com.oneutility.cba.bankingapi.auth.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /v1/auth/token}: OAuth 2.0 client credentials. */
@RestController
class TokenController {

    private final TokenService tokens;

    TokenController(TokenService tokens) {
        this.tokens = tokens;
    }

    @PostMapping(path = "/v1/auth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TokenResponse> token(@RequestParam(name = "grant_type", required = false) String grantType,
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "client_secret", required = false) String clientSecret,
            @RequestParam(name = "scope", required = false) String scope,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        TokenResponse body = TokenResponse.from(tokens.issue(grantType, clientId, clientSecret, authorization, scope));
        return noStore(ResponseEntity.ok()).body(body);
    }

    @ExceptionHandler(TokenRequestException.class)
    ResponseEntity<TokenErrorResponse> refused(TokenRequestException e, HttpServletRequest request) {
        ResponseEntity.BodyBuilder response = noStore(ResponseEntity.status(e.status()))
                .contentType(MediaType.APPLICATION_JSON);
        // RFC 6749 section 5.2: the challenge is required only when the client used HTTP Basic. Sent to a browser
        // (the docs page's "Try it" and "Authorize", which also put the credentials in the form), it opens the
        // browser's own sign-in box, so it goes only to clients that used HTTP Basic alone.
        if ("invalid_client".equals(e.error()) && usedBasicOnly(request)) {
            response.header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"banking-api\"");
        }
        return response.body(new TokenErrorResponse(e.error(), e.getMessage()));
    }

    private static boolean usedBasicOnly(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authorization != null && authorization.regionMatches(true, 0, "Basic ", 0, 6)
                && request.getParameter("client_secret") == null;
    }

    // RFC 6749 section 5.1: token responses are never cached.
    private static ResponseEntity.BodyBuilder noStore(ResponseEntity.BodyBuilder response) {
        return response.cacheControl(CacheControl.noStore()).header(HttpHeaders.PRAGMA, "no-cache");
    }
}
