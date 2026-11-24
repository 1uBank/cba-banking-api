package com.oneutility.cba.bankingapi.onus.service;

import com.oneutility.cba.bankingapi.accounts.service.AccountService;
import com.oneutility.cba.bankingapi.common.exception.ApiException;
import com.oneutility.cba.bankingapi.common.exception.ErrorCode;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreAccount;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreDebit;
import com.oneutility.cba.bankingapi.corebanking.dto.CorePaymentType;
import com.oneutility.cba.bankingapi.corebanking.service.CoreBankingException;
import com.oneutility.cba.bankingapi.corebanking.service.CoreBankingTransfers;
import com.oneutility.cba.bankingapi.corebanking.service.CoreBankingVirtualAccounts;
import com.oneutility.cba.bankingapi.onus.dto.PaymentType;
import com.oneutility.cba.bankingapi.onus.dto.ServiceDebit;
import com.oneutility.cba.bankingapi.onus.dto.ServiceDebitRequest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * Service debits: a withdrawal from one of the client's customers' accounts with any payment type the bank has set
 * up, which decides where the money goes in the ledger. Final in the response. Static virtual accounts cannot be
 * debited: their money can only go to their parent.
 */
@Service
public class DebitService {

    private static final ZoneId BANK_ZONE = ZoneId.of("Africa/Lagos");

    private final AccountService accountService;
    private final CoreBankingTransfers transfers;
    private final CoreBankingVirtualAccounts virtualAccounts;

    public DebitService(AccountService accountService, CoreBankingTransfers transfers, CoreBankingVirtualAccounts virtualAccounts) {
        this.accountService = accountService;
        this.transfers = transfers;
        this.virtualAccounts = virtualAccounts;
    }

    /** Debits the account; a resend with the same reference returns the first debit. */
    public ServiceDebit debit(ServiceDebitRequest request) {
        BigDecimal amount = new BigDecimal(request.amount());
        CoreAccount account = accountService.requireOwned(request.accountNumber());
        Optional<CoreDebit> earlier = call(() -> transfers.findDebit(account.savingsId(), request.transactionReference()));
        if (earlier.isPresent()) {
            CoreDebit e = earlier.get();
            if (e.amount().compareTo(amount) != 0 || e.paymentType() == null || !e.paymentType().equalsIgnoreCase(request.paymentType().trim())) {
                throw reused(request);
            }
            return debit(e);
        }
        if (call(() -> virtualAccounts.findByNumber(request.accountNumber())).isPresent()) {
            throw new ApiException(ErrorCode.ACCOUNT_RESTRICTED, "A virtual account cannot be debited; its money can only go to its parent");
        }
        CorePaymentType paymentType = call(() -> transfers.paymentType(request.paymentType())).orElseThrow(() -> new ApiException(
                ErrorCode.PAYMENT_TYPE_UNKNOWN, "The bank has no payment type '" + request.paymentType().trim() + "'"));
        try {
            return debit(transfers.debit(account.savingsId(), paymentType, amount,
                    request.narration() == null ? null : request.narration().trim(), request.transactionReference()));
        } catch (CoreBankingException e) {
            if (e.codes().stream().anyMatch(code -> code.equals(CoreBankingTransfers.REFERENCE_USED_ELSEWHERE)
                    || code.contains("external.id") || code.contains("externalId") || code.contains("duplicate"))) {
                throw reused(request);
            }
            throw AccountService.refusedOnAccount(e);
        }
    }

    public List<PaymentType> paymentTypes() {
        return call(transfers::paymentTypes).stream().map(p -> new PaymentType(p.name(), p.description())).toList();
    }

    private static ApiException reused(ServiceDebitRequest request) {
        return new ApiException(ErrorCode.REFERENCE_REUSED,
                "transactionReference " + request.transactionReference() + " was already used for another request");
    }

    private static ServiceDebit debit(CoreDebit d) {
        // CBA-CORE keeps the debit's date only; the time is when the Banking API answers
        OffsetDateTime when = OffsetDateTime.now(BANK_ZONE).truncatedTo(ChronoUnit.SECONDS);
        if (!when.toLocalDate().equals(d.date())) {
            when = d.date().atStartOfDay(BANK_ZONE).toOffsetDateTime();
        }
        return new ServiceDebit(d.transactionReference(), Long.toString(d.transactionId()), d.accountNumber(), d.paymentType(),
                TransferService.money(d.amount()), d.narration(), "CONFIRMED", when);
    }

    private static <T> T call(Supplier<T> coreBankingCall) {
        try {
            return coreBankingCall.get();
        } catch (CoreBankingException e) {
            throw AccountService.upstream(e);
        }
    }
}
