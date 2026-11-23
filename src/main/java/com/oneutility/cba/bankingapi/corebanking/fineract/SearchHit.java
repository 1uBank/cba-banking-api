package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One of Fineract's {@code GET /search} results. For a savings account, {@code entityId} is the account's ID,
 * {@code entityAccountNo} its number and {@code parentId} the client's ID.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchHit(Long entityId, String entityAccountNo, String entityType, Long parentId, String parentType) {
}
