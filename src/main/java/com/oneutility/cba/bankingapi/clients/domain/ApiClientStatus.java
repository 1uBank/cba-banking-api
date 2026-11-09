package com.oneutility.cba.bankingapi.clients.domain;

/** Whether a client application may use the API. */
public enum ApiClientStatus {
    /** May get tokens and call the API. */
    ACTIVE,
    /** Refused at the token endpoint and on every call, even with a token that has not expired. */
    SUSPENDED
}
