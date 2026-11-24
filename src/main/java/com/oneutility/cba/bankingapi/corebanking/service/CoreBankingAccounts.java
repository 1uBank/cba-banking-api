package com.oneutility.cba.bankingapi.corebanking.service;

import com.oneutility.cba.bankingapi.corebanking.dto.CoreAccount;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreAccountStatus;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreProduct;
import com.oneutility.cba.bankingapi.corebanking.fineract.ClientSavingsAccount;
import com.oneutility.cba.bankingapi.corebanking.fineract.OpenAccountCommand;
import com.oneutility.cba.bankingapi.corebanking.fineract.SavingsAccountResponse;
import com.oneutility.cba.bankingapi.corebanking.fineract.SavingsStatus;
import com.oneutility.cba.bankingapi.corebanking.fineract.SavingsSubStatus;
import com.oneutility.cba.bankingapi.corebanking.fineract.SearchHit;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.LocalDate;
import com.oneutility.cba.bankingapi.corebanking.fineract.HoldResponse;
import com.oneutility.cba.bankingapi.corebanking.fineract.HoldAmountCommand;
import com.oneutility.cba.bankingapi.corebanking.fineract.AccountCommand;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreHold;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreBlock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Savings accounts and products in CBA-CORE. Ownership is checked by the caller, through the account's customer. */
@Service
public class CoreBankingAccounts {

    private static final ZoneId BANK_ZONE = ZoneId.of("Africa/Lagos");

    private final FineractApi api;

    CoreBankingAccounts(FineractApi api) {
        this.api = api;
    }

    /** The account's number as customers know it: its NUBAN, which CBA-CORE issues as the account number. */
    public String accountNumber(long savingsId) {
        return api.getSavingsAccount(savingsId).accountNo();
    }

    /** The account with its live balances. */
    public CoreAccount account(long savingsId) {
        return account(api.getSavingsAccount(savingsId));
    }

    /** The ID of the account with this number, if there is one. */
    public Optional<Long> findByNumber(String accountNumber) {
        return api.findSavingsAccounts(accountNumber).stream()
                .filter(hit -> accountNumber.equals(hit.entityAccountNo()) && hit.entityId() != null)
                .map(SearchHit::entityId).findFirst();
    }

    /** The customer's accounts, oldest first, with live balances. */
    public List<CoreAccount> customerAccounts(long clientId) {
        return api.getClientAccounts(clientId).savingsAccounts().stream()
                .sorted(Comparator.comparingLong(ClientSavingsAccount::id))
                .map(summary -> account(summary.id())).toList();
    }

    /**
     * Opens and activates an account on the product, in one CBA-CORE transaction.
     *
     * @param idempotencyKey makes a resend return the account first opened; optional
     * @return the new account's ID
     * @throws CoreBankingException when CBA-CORE refuses, for example an unknown product
     */
    public long open(long clientId, long productId, String idempotencyKey) {
        return api.openAccount(clientId, new OpenAccountCommand(productId), idempotencyKey).savingsId();
    }

    // Holds

    /**
     * Holds {@code amount} on the account. The reference is stored on the hold, so a resend with the same reference
     * returns the first hold instead of placing another.
     *
     * @return the hold
     * @throws CoreBankingException when CBA-CORE refuses, for example an account that is not active
     */
    public CoreHold placeHold(long savingsId, BigDecimal amount, String reason, String transactionReference) {
        api.holdAmount(savingsId, HoldAmountCommand.of(amount, LocalDate.now(BANK_ZONE).toString(), reason,
                transactionReference), transactionReference);
        return findHold(transactionReference).orElseThrow(
                () -> new IllegalStateException("CBA-CORE placed a hold but cannot find it by its reference"));
    }

    /** The hold placed with this reference, if any. */
    public Optional<CoreHold> findHold(String transactionReference) {
        return notFoundAsEmpty(() -> hold(api.findHoldByReference(transactionReference)));
    }

    /** The hold with this transaction ID on this account, if any. */
    public Optional<CoreHold> findHold(long savingsId, long holdTransactionId) {
        return notFoundAsEmpty(() -> hold(api.findHoldByTransaction(savingsId, holdTransactionId)));
    }

    /** Releases the hold; the caller checks it is not already released. */
    public void releaseHold(long savingsId, long holdTransactionId) {
        api.releaseAmount(savingsId, holdTransactionId, AccountCommand.empty());
    }

    // Blocks

    /** Adds {@code wanted} to what is blocked on the account; what is already blocked stays as it is. */
    public void block(long savingsId, CoreBlock wanted, String reason) {
        CoreBlock current = account(savingsId).block();
        boolean debit = wanted.debit() && !current.debit();
        boolean credit = wanted.credit() && !current.credit();
        if (debit && credit) {
            command(savingsId, "block", AccountCommand.withReason(reason));
        } else if (debit) {
            command(savingsId, "blockDebit", AccountCommand.withReason(reason));
        } else if (credit) {
            command(savingsId, "blockCredit", AccountCommand.withReason(reason));
        }
    }

    /** Lifts {@code type} from what is blocked on the account; the rest stays blocked. */
    public void unblock(long savingsId, CoreBlock type) {
        CoreBlock current = account(savingsId).block();
        boolean debit = type.debit() && current.debit();
        boolean credit = type.credit() && current.credit();
        if (debit && credit) {
            command(savingsId, "unblock", AccountCommand.empty());
        } else if (debit) {
            command(savingsId, "unblockDebit", AccountCommand.empty());
        } else if (credit) {
            command(savingsId, "unblockCredit", AccountCommand.empty());
        }
    }

    private void command(long savingsId, String command, AccountCommand body) {
        api.savingsAccountCommand(savingsId, command, body);
    }

    private static CoreHold hold(HoldResponse h) {
        return new CoreHold(h.holdTransactionId(), h.savingsId(), h.accountNo(), h.clientId() == null ? 0 : h.clientId(),
                h.amount(), h.reason(), h.transactionReference(), h.createdOn(), h.released(), h.releasedOn());
    }

    private static <T> Optional<T> notFoundAsEmpty(java.util.function.Supplier<T> call) {
        try {
            return Optional.of(call.get());
        } catch (CoreBankingException e) {
            if (e.isNotFound()) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /** The savings products accounts can be opened on. */
    public List<CoreProduct> products() {
        return api.getSavingsProducts().stream()
                .map(p -> new CoreProduct(p.id(), p.name(), p.shortName(), p.description())).toList();
    }

    private static CoreAccount account(SavingsAccountResponse a) {
        return new CoreAccount(a.id(), a.accountNo(), a.clientId() == null ? 0 : a.clientId(), a.clientName(),
                a.savingsProductId() == null ? 0 : a.savingsProductId(), a.savingsProductName(),
                status(a.status(), a.subStatus()), a.currency() == null ? null : a.currency().code(),
                a.summary() == null ? BigDecimal.ZERO : zeroIfNull(a.summary().accountBalance()),
                a.summary() == null ? BigDecimal.ZERO : zeroIfNull(a.summary().availableBalance()), block(a.subStatus()),
                block(a.subStatus()) == CoreBlock.NONE ? null : a.reasonForBlock());
    }

    private static CoreBlock block(SavingsSubStatus subStatus) {
        return subStatus == null ? CoreBlock.NONE
                : CoreBlock.of(subStatus.block() || subStatus.blockDebit(), subStatus.block() || subStatus.blockCredit());
    }

    static CoreAccountStatus status(SavingsStatus status, SavingsSubStatus subStatus) {
        if (status == null) {
            return CoreAccountStatus.PENDING;
        }
        if (status.closed()) {
            return CoreAccountStatus.CLOSED;
        }
        if (!status.active()) {
            return CoreAccountStatus.PENDING;
        }
        if (subStatus != null && (subStatus.block() || subStatus.blockDebit() || subStatus.blockCredit())) {
            return CoreAccountStatus.RESTRICTED;
        }
        if (subStatus != null && subStatus.dormant()) {
            return CoreAccountStatus.DORMANT;
        }
        return CoreAccountStatus.ACTIVE;
    }

    private static BigDecimal zeroIfNull(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
