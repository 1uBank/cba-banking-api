package com.oneutility.cba.bankingapi.shared.error;

/** One invalid field in a {@code VALIDATION_FAILED} error. */
public record FieldViolation(String field, String message) {
}
