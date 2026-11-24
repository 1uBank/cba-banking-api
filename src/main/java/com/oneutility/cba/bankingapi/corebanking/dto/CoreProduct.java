package com.oneutility.cba.bankingapi.corebanking.dto;

/**
 * A savings product. {@code virtualAccountType} is set when the product is for static virtual accounts of that type;
 * those are opened only through the virtual account calls.
 */
public record CoreProduct(long id, String name, String shortName, String description, String virtualAccountType) {

    public boolean forVirtualAccounts() {
        return virtualAccountType != null && !virtualAccountType.isBlank();
    }
}
