package com.oneutility.cba.bankingapi.corebanking.fineract;

/** A row of one of the customer datatables; each record's keys are that datatable's column names. */
public sealed interface DatatableRow permits OwnerRow, KycRow, RiskRow, LimitRow, RelatedPersonRow {
}
