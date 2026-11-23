package com.oneutility.cba.bankingapi.accounts.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** {@code Product}: a savings product, with the {@code id} to send as {@code productId}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Product(long id, String name, String shortName, String description) {
}
