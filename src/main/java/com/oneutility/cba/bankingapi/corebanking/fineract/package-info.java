/**
 * CBA-CORE's wire format: one record per request and answer of the Fineract and 1UBank calls the Banking API makes,
 * and one per customer datatable row (their keys are the datatable's column names). Only {@code corebanking} uses
 * these; the rest of the Banking API sees that module's own records.
 *
 * <p>
 * Answers ignore fields not declared here, so Fineract can add fields without breaking us. Requests leave out
 * {@code null} fields, which Fineract reads as "not sent".
 */
package com.oneutility.cba.bankingapi.corebanking.fineract;
