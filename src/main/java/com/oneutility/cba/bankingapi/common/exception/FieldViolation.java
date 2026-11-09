package com.oneutility.cba.bankingapi.common.exception;

/** One invalid field in a {@code VALIDATION_FAILED} error. */
public record FieldViolation(String field, String message) {
}
