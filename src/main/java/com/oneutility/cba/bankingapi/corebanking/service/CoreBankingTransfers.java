package com.oneutility.cba.bankingapi.corebanking.service;

import com.oneutility.cba.bankingapi.corebanking.dto.CoreDebit;
import com.oneutility.cba.bankingapi.corebanking.dto.CorePaymentType;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreTransfer;
import com.oneutility.cba.bankingapi.corebanking.fineract.AccountTransferCommand;
import com.oneutility.cba.bankingapi.corebanking.fineract.CommandResult;
import com.oneutility.cba.bankingapi.corebanking.fineract.SavingsAccountResponse;
import com.oneutility.cba.bankingapi.corebanking.fineract.SavingsTransactionResponse;
import com.oneutility.cba.bankingapi.corebanking.fineract.TransferResponse;
import com.oneutility.cba.bankingapi.corebanking.fineract.WithdrawalCommand;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * Money moving on-us in CBA-CORE: transfers between two 1UBank accounts (Fineract's account transfer, one record with
 * both postings) and debits for a service (a withdrawal with a payment type). The client's reference makes each
 * one idempotent: it is sent as the {@code Idempotency-Key}, and a debit also stores it as its external ID.
 */
@Service
public class CoreBankingTransfers {

    private static final ZoneId BANK_ZONE = ZoneId.of("Africa/Lagos");
    /** The code of the refusal when a reference was already used on another account. */
    public static final String REFERENCE_USED_ELSEWHERE = "oneubank.reference.used.elsewhere";

    private final FineractApi api;

    CoreBankingTransfers(FineractApi api) {
        this.api = api;
    }

    /**
     * Moves {@code amount} from one savings account to another.
     *
     * @throws CoreBankingException when CBA-CORE refuses, for example a balance too low or a blocked account
     */
    public CoreTransfer transfer(long fromSavingsId, long toSavingsId, BigDecimal amount, String description, String reference) {
        SavingsAccountResponse from = api.getSavingsAccount(fromSavingsId);
        SavingsAccountResponse to = api.getSavingsAccount(toSavingsId);
        long fromOffice = api.getClient(from.clientId()).officeId();
        long toOffice = api.getClient(to.clientId()).officeId();
        api.createAccountTransfer(AccountTransferCommand.savingsToSavings(fromOffice, from.clientId(), fromSavingsId, toOffice,
                to.clientId(), toSavingsId, amount, LocalDate.now(BANK_ZONE).toString(), description), reference);
        return findTransfer(reference).orElseThrow(
                () -> new IllegalStateException("CBA-CORE made a transfer but cannot find it by its reference"));
    }

    /** The transfer made with this reference, if any. */
    public Optional<CoreTransfer> findTransfer(String reference) {
        return notFoundAsEmpty(() -> transfer(api.findTransfer(reference)));
    }

    /**
     * Debits the account with the payment type.
     *
     * @throws CoreBankingException when CBA-CORE refuses, for example a balance too low or a reference already used
     */
    public CoreDebit debit(long savingsId, CorePaymentType paymentType, BigDecimal amount, String narration, String reference) {
        CommandResult result = api.withdraw(savingsId,
                WithdrawalCommand.of(LocalDate.now(BANK_ZONE).toString(), amount, paymentType.id(), reference, narration), reference);
        if (result.savingsId() != null && result.savingsId() != savingsId) {
            // Fineract replays a command by its Idempotency-Key alone: this reference was used on another account
            throw new CoreBankingException(CoreBankingException.Kind.REFUSED, 409, List.of(REFERENCE_USED_ELSEWHERE),
                    "The reference was already used on another account; nothing was posted");
        }
        return findDebit(savingsId, reference).orElseThrow(
                () -> new IllegalStateException("CBA-CORE made a debit but cannot find it by its reference"));
    }

    /** The debit made on this account with this reference, if any. */
    public Optional<CoreDebit> findDebit(long savingsId, String reference) {
        return notFoundAsEmpty(() -> debit(savingsId, api.getTransactionByExternalId(savingsId, reference)));
    }

    public List<CorePaymentType> paymentTypes() {
        return api.getPaymentTypes().stream().map(p -> new CorePaymentType(p.id(), p.name(), p.description())).toList();
    }

    /** The payment type with this name (any case), if CBA-CORE has one. */
    public Optional<CorePaymentType> paymentType(String name) {
        return paymentTypes().stream().filter(p -> p.name().trim().equalsIgnoreCase(name.trim())).findFirst();
    }

    private static CoreTransfer transfer(TransferResponse t) {
        return new CoreTransfer(t.transferId(), t.transactionReference(), t.fromSavingsId(), t.fromAccountNo(), t.fromClientId(),
                t.toSavingsId(), t.toAccountNo(), t.toClientName(), t.amount(), t.description(), t.createdOn(), t.reversed());
    }

    private static CoreDebit debit(long savingsId, SavingsTransactionResponse t) {
        return new CoreDebit(t.id(), savingsId, t.accountNo(), t.externalId(), t.paymentTypeName(), t.amount(), t.note(), t.date(),
                t.reversed());
    }

    private static <T> Optional<T> notFoundAsEmpty(Supplier<T> call) {
        try {
            return Optional.of(call.get());
        } catch (CoreBankingException e) {
            if (e.isNotFound()) {
                return Optional.empty();
            }
            throw e;
        }
    }
}
