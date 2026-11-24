package com.oneutility.cba.bankingapi.onus.service;

import com.oneutility.cba.bankingapi.accounts.service.AccountService;
import com.oneutility.cba.bankingapi.common.exception.ApiException;
import com.oneutility.cba.bankingapi.common.exception.ErrorCode;
import com.oneutility.cba.bankingapi.common.exception.FieldViolation;
import com.oneutility.cba.bankingapi.common.security.CurrentClient;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreAccount;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreHold;
import com.oneutility.cba.bankingapi.corebanking.service.CoreBankingAccounts;
import com.oneutility.cba.bankingapi.corebanking.service.CoreBankingCustomers;
import com.oneutility.cba.bankingapi.corebanking.service.CoreBankingException;
import com.oneutility.cba.bankingapi.onus.dto.Hold;
import com.oneutility.cba.bankingapi.onus.dto.PlaceHoldRequest;
import com.oneutility.cba.bankingapi.onus.dto.ReleaseHoldRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * Holds on accounts, with Fineract's own hold and release. A client places, reads and releases holds on its own
 * customers' accounts, including holds bank staff placed in the back office.
 */
@Service
public class HoldService {

    private static final ZoneId BANK_ZONE = ZoneId.of("Africa/Lagos");

    private final AccountService accountService;
    private final CoreBankingAccounts accounts;
    private final CoreBankingCustomers customers;

    public HoldService(AccountService accountService, CoreBankingAccounts accounts, CoreBankingCustomers customers) {
        this.accountService = accountService;
        this.accounts = accounts;
        this.customers = customers;
    }

    /** Places the hold; a resend with the same reference returns the first hold. */
    public Hold place(PlaceHoldRequest request) {
        Optional<CoreHold> earlier = call(() -> accounts.findHold(request.transactionReference()));
        if (earlier.isPresent()) {
            return sameHoldOrConflict(earlier.get(), request);
        }
        CoreAccount account = accountService.requireOwned(request.accountNumber());
        try {
            return hold(accounts.placeHold(account.savingsId(), new BigDecimal(request.amount()), request.reason().trim(),
                    request.transactionReference()));
        } catch (CoreBankingException e) {
            throw AccountService.refusedOnAccount(e);
        }
    }

    public Hold get(String transactionReference) {
        return hold(owned(call(() -> accounts.findHold(transactionReference))));
    }

    /** Releases the hold; releasing it again returns the released hold. */
    public Hold release(ReleaseHoldRequest request) {
        CoreHold hold;
        if (request.byReference()) {
            hold = owned(call(() -> accounts.findHold(request.transactionReference())));
        } else if (request.byHoldTransactionId()) {
            CoreAccount account = accountService.requireOwned(request.accountNumber());
            hold = owned(call(() -> accounts.findHold(account.savingsId(), request.holdTransactionId())));
        } else {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Send transactionReference, or accountNumber with holdTransactionId",
                    List.of(new FieldViolation("transactionReference",
                            "send transactionReference, or accountNumber with holdTransactionId")));
        }
        if (hold.released()) {
            return hold(hold);
        }
        try {
            accounts.releaseHold(hold.savingsId(), hold.holdTransactionId());
        } catch (CoreBankingException e) {
            throw AccountService.refusedOnAccount(e);
        }
        return hold(call(() -> accounts.findHold(hold.savingsId(), hold.holdTransactionId())).orElseThrow());
    }

    private Hold sameHoldOrConflict(CoreHold earlier, PlaceHoldRequest request) {
        boolean same = earlier.accountNumber().equals(request.accountNumber())
                && earlier.amount().compareTo(new BigDecimal(request.amount())) == 0;
        if (!same || !ownedBy(earlier)) {
            throw new ApiException(ErrorCode.REFERENCE_REUSED,
                    "transactionReference " + request.transactionReference() + " was already used for another request");
        }
        return hold(earlier);
    }

    /** The hold, if it is on an account of one of the client's customers; otherwise {@code HOLD_NOT_FOUND}. */
    private CoreHold owned(Optional<CoreHold> hold) {
        if (hold.isEmpty() || !ownedBy(hold.get())) {
            throw new ApiException(ErrorCode.HOLD_NOT_FOUND, "No such hold for this client");
        }
        return hold.get();
    }

    private boolean ownedBy(CoreHold hold) {
        Optional<String> owner = call(() -> customers.owner(hold.clientId()));
        return owner.isPresent() && owner.get().equals(CurrentClient.id());
    }

    private static Hold hold(CoreHold h) {
        return new Hold(h.transactionReference(), h.holdTransactionId(), h.accountNumber(), h.savingsId(), h.clientId(),
                h.amount().setScale(2, RoundingMode.HALF_EVEN).toPlainString(), h.reason(),
                h.released() ? "RELEASED" : "ACTIVE", local(h.createdOn()), local(h.releasedOn()));
    }

    private static OffsetDateTime local(OffsetDateTime time) {
        return time == null ? null : time.atZoneSameInstant(BANK_ZONE).toOffsetDateTime();
    }

    private static <T> T call(Supplier<T> coreBankingCall) {
        try {
            return coreBankingCall.get();
        } catch (CoreBankingException e) {
            throw AccountService.upstream(e);
        }
    }
}
