/**
 * Cross-cutting code every module uses: the error format, correlation IDs and security.
 *
 * <p>This package depends on no business module. Modules plug in through its interfaces (for example
 * {@link com.oneutility.cba.bankingapi.shared.security.ActiveClientCheck}).
 */
package com.oneutility.cba.bankingapi.shared;
