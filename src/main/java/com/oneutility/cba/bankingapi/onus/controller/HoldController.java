package com.oneutility.cba.bankingapi.onus.controller;

import com.oneutility.cba.bankingapi.onus.dto.Hold;
import com.oneutility.cba.bankingapi.onus.dto.PlaceHoldRequest;
import com.oneutility.cba.bankingapi.onus.dto.ReleaseHoldRequest;
import com.oneutility.cba.bankingapi.onus.service.HoldService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The contract's Holds endpoints. Scope {@code transfers}. */
@RestController
@RequestMapping(path = "/v1/holds", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAuthority('SCOPE_transfers')")
class HoldController {

    private final HoldService holds;

    HoldController(HoldService holds) {
        this.holds = holds;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    Hold place(@Valid @RequestBody PlaceHoldRequest request) {
        return holds.place(request);
    }

    @PostMapping(path = "/release", consumes = MediaType.APPLICATION_JSON_VALUE)
    Hold release(@Valid @RequestBody ReleaseHoldRequest request) {
        return holds.release(request);
    }

    @GetMapping("/{transactionReference}")
    Hold get(@PathVariable String transactionReference) {
        return holds.get(transactionReference);
    }
}
