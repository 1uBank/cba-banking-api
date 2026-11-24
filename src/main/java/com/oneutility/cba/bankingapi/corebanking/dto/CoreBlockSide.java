package com.oneutility.cba.bankingapi.corebanking.dto;

import java.time.OffsetDateTime;

/**
 * One blocked side of an account ({@code DEBIT} or {@code CREDIT}). {@code placedByClient} is false for blocks bank
 * staff or compliance placed, and for blocks of unknown origin.
 */
public record CoreBlockSide(String type, boolean placedByClient, String reason, OffsetDateTime placedOn) {
}
