package com.oneutility.cba.bankingapi.customers.controller;

import com.oneutility.cba.bankingapi.customers.dto.CreateBusinessRequest;
import com.oneutility.cba.bankingapi.customers.dto.CreateIndividualRequest;
import com.oneutility.cba.bankingapi.customers.dto.CreateCustomerResponse;
import com.oneutility.cba.bankingapi.customers.dto.Customer;
import com.oneutility.cba.bankingapi.customers.dto.CustomerPage;
import com.oneutility.cba.bankingapi.customers.dto.CustomerSearchRequest;
import com.oneutility.cba.bankingapi.customers.dto.KycLevel;
import com.oneutility.cba.bankingapi.customers.dto.RiskProfile;
import com.oneutility.cba.bankingapi.customers.dto.SetKycLevelRequest;
import com.oneutility.cba.bankingapi.customers.dto.SetRiskProfileRequest;
import com.oneutility.cba.bankingapi.customers.dto.SetTransactionLimitRequest;
import com.oneutility.cba.bankingapi.customers.dto.TransactionLimit;
import com.oneutility.cba.bankingapi.customers.dto.UpdateBusinessRequest;
import com.oneutility.cba.bankingapi.customers.dto.UpdateIndividualRequest;
import com.oneutility.cba.bankingapi.customers.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The contract's Customers endpoints. Scope {@code customers}. */
@RestController
@RequestMapping(path = "/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAuthority('SCOPE_customers')")
class CustomerController {

    private final CustomerService customers;

    CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    @PostMapping(path = "/individual-customers", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    CreateCustomerResponse createIndividual(@Valid @RequestBody CreateIndividualRequest request) {
        return customers.createIndividual(request);
    }

    @PostMapping(path = "/business-customers", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    CreateCustomerResponse createBusiness(@Valid @RequestBody CreateBusinessRequest request) {
        return customers.createBusiness(request);
    }

    @PutMapping(path = "/individual-customers/{clientId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    Customer updateIndividual(@PathVariable long clientId, @Valid @RequestBody UpdateIndividualRequest request) {
        return customers.updateIndividual(clientId, request);
    }

    @PutMapping(path = "/business-customers/{clientId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    Customer updateBusiness(@PathVariable long clientId, @Valid @RequestBody UpdateBusinessRequest request) {
        return customers.updateBusiness(clientId, request);
    }

    @GetMapping("/customers/{clientId}")
    Customer get(@PathVariable long clientId) {
        return customers.get(clientId);
    }

    @PostMapping(path = "/customers/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    CustomerPage search(@Valid @RequestBody CustomerSearchRequest request,
            @RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor) {
        return customers.search(request, limit, cursor);
    }

    @PutMapping(path = "/customers/{clientId}/kyc-level", consumes = MediaType.APPLICATION_JSON_VALUE)
    KycLevel setKycLevel(@PathVariable long clientId, @Valid @RequestBody SetKycLevelRequest request) {
        return customers.setKycLevel(clientId, request.kycLevel(), request.clientId());
    }

    @PutMapping(path = "/customers/{clientId}/risk-profile", consumes = MediaType.APPLICATION_JSON_VALUE)
    RiskProfile setRiskProfile(@PathVariable long clientId, @Valid @RequestBody SetRiskProfileRequest request) {
        return customers.setRiskProfile(clientId, request);
    }

    @PutMapping(path = "/customers/{clientId}/transaction-limit", consumes = MediaType.APPLICATION_JSON_VALUE)
    TransactionLimit setTransactionLimit(@PathVariable long clientId,
            @Valid @RequestBody SetTransactionLimitRequest request) {
        return customers.setTransactionLimit(clientId, request.transactionLimit());
    }
}
