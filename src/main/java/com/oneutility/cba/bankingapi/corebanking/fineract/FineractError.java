package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Fineract's error body. {@code userMessageGlobalisationCode} is the stable code to act on; {@code errors} lists the
 * parameter or domain-rule failures behind it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FineractError(String userMessageGlobalisationCode, String defaultUserMessage, List<Detail> errors) {

    public FineractError {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Detail(String userMessageGlobalisationCode, String defaultUserMessage, String parameterName) {
    }
}
