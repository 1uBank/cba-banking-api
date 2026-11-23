package com.oneutility.cba.bankingapi.corebanking.service;

import com.oneutility.cba.bankingapi.corebanking.fineract.ClientAccountsResponse;
import com.oneutility.cba.bankingapi.corebanking.fineract.ClientAddressResponse;
import com.oneutility.cba.bankingapi.corebanking.fineract.ClientIdentifierResponse;
import com.oneutility.cba.bankingapi.corebanking.fineract.ClientResponse;
import com.oneutility.cba.bankingapi.corebanking.fineract.CodeResponse;
import com.oneutility.cba.bankingapi.corebanking.fineract.CodeValueResponse;
import com.oneutility.cba.bankingapi.corebanking.fineract.CommandResult;
import com.oneutility.cba.bankingapi.corebanking.fineract.CreateCustomerCommand;
import com.oneutility.cba.bankingapi.corebanking.fineract.CustomerSearchQuery;
import com.oneutility.cba.bankingapi.corebanking.fineract.CustomerSearchResult;
import com.oneutility.cba.bankingapi.corebanking.fineract.DatatableRow;
import com.oneutility.cba.bankingapi.corebanking.fineract.KycRow;
import com.oneutility.cba.bankingapi.corebanking.fineract.LimitRow;
import com.oneutility.cba.bankingapi.corebanking.fineract.OpenAccountCommand;
import com.oneutility.cba.bankingapi.corebanking.fineract.SearchHit;
import com.oneutility.cba.bankingapi.corebanking.fineract.OwnerRow;
import com.oneutility.cba.bankingapi.corebanking.fineract.RelatedPersonRow;
import com.oneutility.cba.bankingapi.corebanking.fineract.RiskRow;
import com.oneutility.cba.bankingapi.corebanking.fineract.SavingsAccountResponse;
import com.oneutility.cba.bankingapi.corebanking.fineract.SavingsProductResponse;
import com.oneutility.cba.bankingapi.corebanking.fineract.UpdateCustomerCommand;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

/**
 * CBA-CORE's API (Fineract plus 1UBank's {@code /oneubank/} calls), as Spring generates the client from it. Paths are
 * relative to {@code banking-api.core-banking.base-url}. Requests and answers are the records in
 * {@code corebanking.fineract}; the services in this package turn them into the module's own records.
 */
interface FineractApi {

    String IDEMPOTENCY_KEY = "Idempotency-Key";
    String DATATABLE_ROWS = "/datatables/{datatable}/{clientId}";

    // 1UBank customer calls

    /** Creates a customer, their identifiers and first account in one transaction. */
    @PostExchange("/oneubank/customers")
    CommandResult createCustomer(@RequestBody CreateCustomerCommand command,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey);

    /** Updates a customer in one transaction (client fields, identifiers, address, related persons). */
    @PutExchange("/oneubank/customers/{clientId}")
    CommandResult updateCustomer(@PathVariable long clientId, @RequestBody UpdateCustomerCommand command);

    /** Opens and activates another account for the customer in one transaction. */
    @PostExchange("/oneubank/customers/{clientId}/accounts")
    CommandResult openAccount(@PathVariable long clientId, @RequestBody OpenAccountCommand command,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey);

    @PostExchange("/oneubank/customers/search")
    CustomerSearchResult searchCustomers(@RequestBody CustomerSearchQuery query);

    // Clients

    @GetExchange("/clients/{clientId}")
    ClientResponse getClient(@PathVariable long clientId);

    @GetExchange("/client/{clientId}/addresses")
    List<ClientAddressResponse> getAddresses(@PathVariable long clientId);

    @GetExchange("/clients/{clientId}/identifiers")
    List<ClientIdentifierResponse> getIdentifiers(@PathVariable long clientId);

    // Customer datatables: one typed read per table; pass the record's TABLE. Names contain spaces, which the URI
    // template encodes.

    @GetExchange(DATATABLE_ROWS + "?genericResultSet=false")
    List<OwnerRow> getOwnerRows(@PathVariable String datatable, @PathVariable long clientId);

    @GetExchange(DATATABLE_ROWS + "?genericResultSet=false")
    List<KycRow> getKycRows(@PathVariable String datatable, @PathVariable long clientId);

    @GetExchange(DATATABLE_ROWS + "?genericResultSet=false")
    List<RiskRow> getRiskRows(@PathVariable String datatable, @PathVariable long clientId);

    @GetExchange(DATATABLE_ROWS + "?genericResultSet=false")
    List<LimitRow> getLimitRows(@PathVariable String datatable, @PathVariable long clientId);

    @GetExchange(DATATABLE_ROWS + "?genericResultSet=false")
    List<RelatedPersonRow> getRelatedPersonRows(@PathVariable String datatable, @PathVariable long clientId);

    /** Adds a row to a one-row-per-customer datatable. */
    @PostExchange(DATATABLE_ROWS)
    CommandResult createDatatableRow(@PathVariable String datatable, @PathVariable long clientId,
            @RequestBody DatatableRow row);

    /** Changes the fields sent in a one-row-per-customer datatable's row. */
    @PutExchange(DATATABLE_ROWS)
    CommandResult updateDatatableRow(@PathVariable String datatable, @PathVariable long clientId,
            @RequestBody DatatableRow row);

    // Code values (dropdown lists)

    @GetExchange("/codes")
    List<CodeResponse> getCodes();

    @GetExchange("/codes/{codeId}/codevalues")
    List<CodeValueResponse> getCodeValues(@PathVariable long codeId);

    // Savings accounts and products

    @GetExchange("/savingsaccounts/{savingsId}")
    SavingsAccountResponse getSavingsAccount(@PathVariable long savingsId);

    @GetExchange("/clients/{clientId}/accounts?fields=savingsAccounts")
    ClientAccountsResponse getClientAccounts(@PathVariable long clientId);

    /** Savings accounts whose number is exactly {@code accountNumber}. */
    @GetExchange("/search?resource=savingsaccounts&exactMatch=true")
    List<SearchHit> findSavingsAccounts(@RequestParam("query") String accountNumber);

    @GetExchange("/savingsproducts")
    List<SavingsProductResponse> getSavingsProducts();
}
