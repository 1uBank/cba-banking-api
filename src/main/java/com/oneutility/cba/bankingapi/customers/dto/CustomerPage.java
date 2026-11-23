package com.oneutility.cba.bankingapi.customers.dto;

import java.util.List;

/** A page of customers; {@code nextCursor} is absent on the last page. */
public record CustomerPage(List<Customer> items, String nextCursor) {
}
