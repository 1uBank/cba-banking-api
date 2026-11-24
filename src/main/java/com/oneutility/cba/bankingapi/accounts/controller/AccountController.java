package com.oneutility.cba.bankingapi.accounts.controller;

import com.oneutility.cba.bankingapi.accounts.dto.Account;
import com.oneutility.cba.bankingapi.accounts.dto.AccountBlocks;
import com.oneutility.cba.bankingapi.accounts.dto.BlockType;
import com.oneutility.cba.bankingapi.accounts.dto.PlaceBlockRequest;
import com.oneutility.cba.bankingapi.accounts.dto.AccountPage;
import com.oneutility.cba.bankingapi.accounts.dto.AccountSearchRequest;
import com.oneutility.cba.bankingapi.accounts.dto.OpenAccountRequest;
import com.oneutility.cba.bankingapi.accounts.dto.Product;
import com.oneutility.cba.bankingapi.accounts.service.AccountService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The contract's Accounts endpoints and the product list. Scope {@code accounts}. */
@RestController
@RequestMapping(path = "/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAuthority('SCOPE_accounts')")
class AccountController {

    private final AccountService accounts;

    AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/customers/{clientId}/accounts")
    AccountPage customerAccounts(@PathVariable long clientId, @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        return accounts.customerAccounts(clientId, limit, cursor);
    }

    @PostMapping(path = "/customers/{clientId}/accounts", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    Account open(@PathVariable long clientId, @Valid @RequestBody OpenAccountRequest request) {
        return accounts.open(clientId, request);
    }

    @GetMapping("/accounts/{accountNumber}")
    Account get(@PathVariable String accountNumber) {
        return accounts.get(accountNumber);
    }

    @PostMapping(path = "/accounts/{accountNumber}/blocks", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    AccountBlocks block(@PathVariable String accountNumber, @Valid @RequestBody PlaceBlockRequest request) {
        return accounts.block(accountNumber, request);
    }

    @GetMapping("/accounts/{accountNumber}/blocks")
    AccountBlocks blocks(@PathVariable String accountNumber) {
        return accounts.blocks(accountNumber);
    }

    @PostMapping("/accounts/{accountNumber}/blocks/{type}/lift")
    AccountBlocks lift(@PathVariable String accountNumber, @PathVariable BlockType type) {
        return accounts.lift(accountNumber, type);
    }

    @PostMapping(path = "/accounts/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    AccountPage search(@Valid @RequestBody AccountSearchRequest request, @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        return accounts.searchByBvn(request.bvn(), limit, cursor);
    }

    @GetMapping("/products")
    List<Product> products() {
        return accounts.products();
    }
}
