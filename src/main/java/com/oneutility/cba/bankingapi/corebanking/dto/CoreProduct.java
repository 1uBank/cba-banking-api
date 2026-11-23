package com.oneutility.cba.bankingapi.corebanking.dto;

/** A savings product accounts can be opened on. */
public record CoreProduct(long id, String name, String shortName, String description) {
}
