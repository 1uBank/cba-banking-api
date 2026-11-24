package com.oneutility.cba.bankingapi.onus.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** {@code PaymentType}: send {@code name} as {@code paymentType} on a service debit. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentType(String name, String description) {
}
