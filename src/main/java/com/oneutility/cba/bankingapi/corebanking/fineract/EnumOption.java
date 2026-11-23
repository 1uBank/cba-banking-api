package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One of Fineract's fixed options (a status, a legal form): {@code code} is the stable value to compare. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EnumOption(Long id, String code, String value) {
}
