package com.oneutility.cba.bankingapi.shared.security;

/**
 * Whether a client application may use the API right now. Implemented by the {@code clients} module; checked when a
 * client asks for a token and on every call it makes with one.
 */
public interface ActiveClientCheck {

    boolean isActive(String clientId);
}
