package com.oneutility.cba.bankingapi.onus.controller;

import com.oneutility.cba.bankingapi.onus.dto.IntraBankTransferRequest;
import com.oneutility.cba.bankingapi.onus.dto.Transfer;
import com.oneutility.cba.bankingapi.onus.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Intra-bank transfers and the transfer lookup by reference. Scope {@code transfers}. */
@RestController
@RequestMapping(path = "/v1/transfers", produces = MediaType.APPLICATION_JSON_VALUE)
class TransferController {

    private final TransferService transfers;

    TransferController(TransferService transfers) {
        this.transfers = transfers;
    }

    @PostMapping(path = "/intra", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('SCOPE_transfers')")
    Transfer intraBank(@Valid @RequestBody IntraBankTransferRequest request) {
        return transfers.intraBank(request);
    }

    /** Interbank transfers are looked up here too once the interbank module exists; for now intra-bank only. */
    @GetMapping("/{transactionReference}")
    @PreAuthorize("hasAuthority('SCOPE_transfers') or hasAuthority('SCOPE_interbank')")
    Transfer get(@PathVariable String transactionReference) {
        return transfers.get(transactionReference);
    }
}
