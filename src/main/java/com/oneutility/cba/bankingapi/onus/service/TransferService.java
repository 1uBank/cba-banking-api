package com.oneutility.cba.bankingapi.onus.service;

import com.oneutility.cba.bankingapi.accounts.service.AccountService;
import com.oneutility.cba.bankingapi.common.exception.ApiException;
import com.oneutility.cba.bankingapi.common.exception.ErrorCode;
import com.oneutility.cba.bankingapi.common.exception.FieldViolation;
import com.oneutility.cba.bankingapi.common.security.CurrentClient;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreAccount;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreAccountStatus;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreTransfer;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreVirtualAccount;
import com.oneutility.cba.bankingapi.corebanking.service.CoreBankingAccounts;
import com.oneutility.cba.bankingapi.corebanking.service.CoreBankingCustomers;
import com.oneutility.cba.bankingapi.corebanking.service.CoreBankingException;
import com.oneutility.cba.bankingapi.corebanking.service.CoreBankingTransfers;
import com.oneutility.cba.bankingapi.corebanking.service.CoreBankingVirtualAccounts;
import com.oneutility.cba.bankingapi.onus.dto.IntraBankTransferRequest;
import com.oneutility.cba.bankingapi.onus.dto.Transfer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * Intra-bank transfers: final in the response. The source must be one of the client's customers' accounts; the
 * destination can be any open 1UBank account. Money in a static virtual account can only go to its parent.
 */
@Service
public class TransferService {

    /** 1UBank's NIBSS institution code. */
    public static final String INSTITUTION_CODE = "090605";
    private static final ZoneId BANK_ZONE = ZoneId.of("Africa/Lagos");

    private final AccountService accountService;
    private final CoreBankingAccounts accounts;
    private final CoreBankingCustomers customers;
    private final CoreBankingTransfers transfers;
    private final CoreBankingVirtualAccounts virtualAccounts;

    public TransferService(AccountService accountService, CoreBankingAccounts accounts, CoreBankingCustomers customers,
            CoreBankingTransfers transfers, CoreBankingVirtualAccounts virtualAccounts) {
        this.accountService = accountService;
        this.accounts = accounts;
        this.customers = customers;
        this.transfers = transfers;
        this.virtualAccounts = virtualAccounts;
    }

    /** Makes the transfer; a resend with the same reference returns the first transfer. */
    public Transfer intraBank(IntraBankTransferRequest request) {
        BigDecimal amount = new BigDecimal(request.amount());
        Optional<CoreTransfer> earlier = call(() -> transfers.findTransfer(request.transactionReference()));
        if (earlier.isPresent()) {
            return sameOrConflict(earlier.get(), request, amount);
        }
        if (request.sourceAccountNumber().equals(request.destinationAccountNumber())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "The source and destination accounts are the same",
                    List.of(new FieldViolation("destinationAccountNumber", "must differ from sourceAccountNumber")));
        }
        CoreAccount source = accountService.requireOwned(request.sourceAccountNumber());
        CoreAccount destination = destination(request.destinationAccountNumber());
        Optional<CoreVirtualAccount> sourceVa = call(() -> virtualAccounts.findByNumber(request.sourceAccountNumber()));
        if (sourceVa.isPresent() && sourceVa.get().parentSavingsId() != destination.savingsId()) {
            throw new ApiException(ErrorCode.ACCOUNT_RESTRICTED,
                    "Money in a virtual account can only go to its parent account " + sourceVa.get().parentAccountNumber());
        }
        if (request.destinationAccountName() != null && !sameName(request.destinationAccountName(), destination.accountName())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "destinationAccountName does not match the account",
                    List.of(new FieldViolation("destinationAccountName", "does not match the account")));
        }
        String description = request.narration().trim() + " - " + source.accountNumber() + " to " + destination.accountNumber();
        try {
            return transfer(transfers.transfer(source.savingsId(), destination.savingsId(), amount, description,
                    request.transactionReference()));
        } catch (CoreBankingException e) {
            throw AccountService.refusedOnAccount(e);
        }
    }

    /** The intra-bank transfer made with this reference, if its source is one of the client's customers' accounts. */
    public Transfer get(String transactionReference) {
        CoreTransfer transfer = call(() -> transfers.findTransfer(transactionReference)).filter(this::ownedByClient)
                .orElseThrow(() -> new ApiException(ErrorCode.TRANSFER_NOT_FOUND, "No transfer with this reference for this client"));
        return transfer(transfer);
    }

    private CoreAccount destination(String accountNumber) {
        Optional<Long> savingsId = call(() -> accounts.findByNumber(accountNumber));
        CoreAccount account = savingsId.map(id -> call(() -> accounts.account(id))).orElse(null);
        if (account == null || account.status() == CoreAccountStatus.CLOSED || account.status() == CoreAccountStatus.PENDING) {
            throw new ApiException(ErrorCode.ACCOUNT_NOT_FOUND, "No open account " + accountNumber);
        }
        return account;
    }

    private Transfer sameOrConflict(CoreTransfer earlier, IntraBankTransferRequest request, BigDecimal amount) {
        boolean same = earlier.fromAccountNumber().equals(request.sourceAccountNumber())
                && earlier.toAccountNumber().equals(request.destinationAccountNumber()) && earlier.amount().compareTo(amount) == 0;
        if (!same || !ownedByClient(earlier)) {
            throw new ApiException(ErrorCode.REFERENCE_REUSED,
                    "transactionReference " + request.transactionReference() + " was already used for another request");
        }
        return transfer(earlier);
    }

    private boolean ownedByClient(CoreTransfer transfer) {
        Optional<String> owner = call(() -> customers.owner(transfer.fromClientId()));
        return owner.isPresent() && owner.get().equals(CurrentClient.id());
    }

    static boolean sameName(String given, String actual) {
        return actual != null && normalised(given).equals(normalised(actual));
    }

    private static String normalised(String name) {
        return name.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private static Transfer transfer(CoreTransfer t) {
        return new Transfer(t.transactionReference(), Long.toString(t.transferId()), "INTRA_BANK", "CONFIRMED", t.fromAccountNumber(),
                INSTITUTION_CODE, t.toAccountNumber(), t.toAccountName(), money(t.amount()), "0.00", "NGN", t.description(),
                local(t.createdOn()));
    }

    static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
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
