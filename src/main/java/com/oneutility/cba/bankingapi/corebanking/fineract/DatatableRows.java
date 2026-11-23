package com.oneutility.cba.bankingapi.corebanking.fineract;

import java.util.List;

/** All of a client's rows for a datatable, replacing what they had ({@code datatableRows} in a customer update). */
public record DatatableRows(String registeredTableName, List<? extends DatatableRow> rows) {

    public DatatableRows {
        rows = List.copyOf(rows);
    }
}
