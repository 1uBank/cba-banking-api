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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Savings accounts and products in CBA-CORE. Ownership is checked by the caller, through the account's customer. */
@Service
public class CoreBankingAccounts {

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
                a.summary() == null ? BigDecimal.ZERO : zeroIfNull(a.summary().availableBalance()));
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
