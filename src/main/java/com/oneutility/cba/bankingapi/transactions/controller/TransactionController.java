package com.oneutility.cba.bankingapi.transactions.controller;

import com.oneutility.cba.bankingapi.transactions.dto.Transaction;
import com.oneutility.cba.bankingapi.transactions.dto.TransactionPage;
import com.oneutility.cba.bankingapi.transactions.dto.TransactionType;
import com.oneutility.cba.bankingapi.transactions.service.TransactionService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Transaction history and look-ups. Scope {@code accounts}. */
@RestController
@RequestMapping(path = "/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAuthority('SCOPE_accounts')")
class TransactionController {

    private final TransactionService transactions;

    TransactionController(TransactionService transactions) {
        this.transactions = transactions;
    }

    @GetMapping("/accounts/{accountNumber}/transactions")
    TransactionPage history(@PathVariable String accountNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) TransactionType transactionType, @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        return transactions.history(accountNumber, fromDate, toDate, transactionType, limit, cursor);
    }

    @GetMapping("/accounts/{accountNumber}/transactions/{transactionId}")
    Transaction get(@PathVariable String accountNumber, @PathVariable String transactionId) {
        return transactions.get(accountNumber, transactionId);
    }

    @GetMapping("/transactions")
    TransactionPage find(@RequestParam(required = false) String transactionReference, @RequestParam(required = false) String sessionId) {
        return transactions.find(transactionReference, sessionId);
    }
}
