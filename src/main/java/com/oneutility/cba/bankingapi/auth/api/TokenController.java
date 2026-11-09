package com.oneutility.cba.bankingapi.auth.api;

import com.oneutility.cba.bankingapi.auth.application.TokenRequestException;
import com.oneutility.cba.bankingapi.auth.application.TokenService;
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
    ResponseEntity<TokenErrorResponse> refused(TokenRequestException e) {
        ResponseEntity.BodyBuilder response = noStore(ResponseEntity.status(e.status()))
                .contentType(MediaType.APPLICATION_JSON);
        if ("invalid_client".equals(e.error())) {
            response.header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"banking-api\"");
        }
        return response.body(new TokenErrorResponse(e.error(), e.getMessage()));
    }

    // RFC 6749 section 5.1: token responses are never cached.
    private static ResponseEntity.BodyBuilder noStore(ResponseEntity.BodyBuilder response) {
        return response.cacheControl(CacheControl.noStore()).header(HttpHeaders.PRAGMA, "no-cache");
    }
}
