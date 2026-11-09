/**
 * What every module shares: exceptions and the error format ({@code exception}), security ({@code security}) and
 * request plumbing such as correlation IDs ({@code web}).
 *
 * <p>This package depends on no business module. Modules plug in through its interfaces (for example
 * {@link com.oneutility.cba.bankingapi.common.security.ActiveClientCheck}).
 */
package com.oneutility.cba.bankingapi.common;
