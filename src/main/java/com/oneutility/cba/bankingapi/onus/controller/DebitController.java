package com.oneutility.cba.bankingapi.onus.controller;

import com.oneutility.cba.bankingapi.onus.dto.PaymentType;
import com.oneutility.cba.bankingapi.onus.dto.ServiceDebit;
import com.oneutility.cba.bankingapi.onus.dto.ServiceDebitRequest;
import com.oneutility.cba.bankingapi.onus.service.DebitService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Service debits and their payment types. Scope {@code transfers}. */
@RestController
@RequestMapping(path = "/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAuthority('SCOPE_transfers')")
class DebitController {

    private final DebitService debits;

    DebitController(DebitService debits) {
        this.debits = debits;
    }

    @PostMapping(path = "/debits", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    ServiceDebit debit(@Valid @RequestBody ServiceDebitRequest request) {
        return debits.debit(request);
    }

    @GetMapping("/payment-types")
    List<PaymentType> paymentTypes() {
        return debits.paymentTypes();
    }
}
