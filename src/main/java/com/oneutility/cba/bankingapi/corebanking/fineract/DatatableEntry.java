package com.oneutility.cba.bankingapi.corebanking.fineract;

/** One row for a datatable, sent inline with a client create ({@code datatables}). */
public record DatatableEntry(String registeredTableName, DatatableRow data) {
}
