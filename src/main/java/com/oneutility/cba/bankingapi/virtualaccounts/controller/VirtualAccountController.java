package com.oneutility.cba.bankingapi.virtualaccounts.controller;

import com.oneutility.cba.bankingapi.virtualaccounts.dto.CreateVirtualAccountRequest;
import com.oneutility.cba.bankingapi.virtualaccounts.dto.RenameVirtualAccountRequest;
import com.oneutility.cba.bankingapi.virtualaccounts.dto.VirtualAccount;
import com.oneutility.cba.bankingapi.virtualaccounts.dto.VirtualAccountPage;
import com.oneutility.cba.bankingapi.virtualaccounts.dto.VirtualAccountType;
import com.oneutility.cba.bankingapi.virtualaccounts.service.VirtualAccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The contract's static virtual account endpoints. Scope {@code virtual-accounts}. */
@RestController
@RequestMapping(path = "/v1/virtual-accounts", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAuthority('SCOPE_virtual-accounts')")
class VirtualAccountController {

    private final VirtualAccountService virtualAccounts;

    VirtualAccountController(VirtualAccountService virtualAccounts) {
        this.virtualAccounts = virtualAccounts;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    VirtualAccount create(@Valid @RequestBody CreateVirtualAccountRequest request) {
        return virtualAccounts.create(request);
    }

    @GetMapping
    VirtualAccountPage list(@RequestParam(required = false) String parentAccountNumber,
            @RequestParam(required = false) VirtualAccountType type, @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        return virtualAccounts.list(parentAccountNumber, type, limit, cursor);
    }

    @GetMapping("/{accountNumber}")
    VirtualAccount get(@PathVariable String accountNumber) {
        return virtualAccounts.get(accountNumber);
    }

    @PatchMapping(path = "/{accountNumber}", consumes = MediaType.APPLICATION_JSON_VALUE)
    VirtualAccount rename(@PathVariable String accountNumber, @Valid @RequestBody RenameVirtualAccountRequest request) {
        return virtualAccounts.rename(accountNumber, request.label());
    }

    @PostMapping("/{accountNumber}/close")
    VirtualAccount close(@PathVariable String accountNumber) {
        return virtualAccounts.close(accountNumber);
    }
}
