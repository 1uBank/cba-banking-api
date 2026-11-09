package com.oneutility.cba.bankingapi.auth.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/** A refused token request, in the OAuth 2.0 error format (RFC 6749, section 5.2). */
record TokenErrorResponse(@JsonProperty("error") String error,
        @JsonProperty("error_description") String errorDescription) {
}
