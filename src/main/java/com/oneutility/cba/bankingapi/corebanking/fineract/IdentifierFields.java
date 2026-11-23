package com.oneutility.cba.bankingapi.corebanking.fineract;

/** A client identifier (BVN, NIN, CAC registration number) to add or replace. */
public record IdentifierFields(long documentTypeId, String documentKey, String status) {

    public static IdentifierFields active(long documentTypeId, String documentKey) {
        return new IdentifierFields(documentTypeId, documentKey, "Active");
    }
}
