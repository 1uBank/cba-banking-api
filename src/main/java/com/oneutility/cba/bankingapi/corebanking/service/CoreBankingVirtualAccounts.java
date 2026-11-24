package com.oneutility.cba.bankingapi.corebanking.service;

import com.oneutility.cba.bankingapi.corebanking.dto.CoreVirtualAccount;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreVirtualAccountPage;
import com.oneutility.cba.bankingapi.corebanking.fineract.CreateVirtualAccountCommand;
import com.oneutility.cba.bankingapi.corebanking.fineract.RenameVirtualAccountCommand;
import com.oneutility.cba.bankingapi.corebanking.fineract.VirtualAccountListResponse;
import com.oneutility.cba.bankingapi.corebanking.fineract.VirtualAccountResponse;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/** Static virtual accounts in CBA-CORE. Ownership is checked by the caller, through the parent's customer. */
@Service
public class CoreBankingVirtualAccounts {

    private final FineractApi api;

    CoreBankingVirtualAccounts(FineractApi api) {
        this.api = api;
    }

    /**
     * Opens the virtual account under the parent, in one CBA-CORE transaction.
     *
     * @param accountNumber the phone number for {@code AIRTIME}; otherwise {@code null}, and a number is allocated
     * @param transactionReference makes a resend return the first virtual account; optional
     * @return the virtual account
     * @throws CoreBankingException when CBA-CORE refuses, for example a type with no product or a number in use
     */
    public CoreVirtualAccount create(String parentAccountNumber, String type, String label, String accountNumber,
            String transactionReference) {
        long savingsId = api.createVirtualAccount(new CreateVirtualAccountCommand(parentAccountNumber, type, label, accountNumber,
                transactionReference), transactionReference).savingsId();
        return get(savingsId);
    }

    public CoreVirtualAccount get(long savingsId) {
        return virtualAccount(api.getVirtualAccount(savingsId));
    }

    public Optional<CoreVirtualAccount> findByNumber(String accountNumber) {
        return notFoundAsEmpty(() -> virtualAccount(api.findVirtualAccountByNumber(accountNumber)));
    }

    public Optional<CoreVirtualAccount> findByReference(String transactionReference) {
        return notFoundAsEmpty(() -> virtualAccount(api.findVirtualAccountByReference(transactionReference)));
    }

    /** A page of the owner's virtual accounts, oldest first, optionally under one parent and of one type. */
    public CoreVirtualAccountPage list(String owner, Long parentSavingsId, String type, Long afterSavingsId, int limit) {
        VirtualAccountListResponse page = api.listVirtualAccounts(owner, parentSavingsId, type, afterSavingsId, limit);
        return new CoreVirtualAccountPage(page.items().stream().map(CoreBankingVirtualAccounts::virtualAccount).toList(),
                page.hasMore());
    }

    public CoreVirtualAccount rename(long savingsId, String label) {
        api.renameVirtualAccount(savingsId, new RenameVirtualAccountCommand(label));
        return get(savingsId);
    }

    /** Closes it; closing a closed one changes nothing. */
    public CoreVirtualAccount close(long savingsId) {
        api.closeVirtualAccount(savingsId);
        return get(savingsId);
    }

    private static CoreVirtualAccount virtualAccount(VirtualAccountResponse v) {
        return new CoreVirtualAccount(v.savingsId(), v.accountNumber(), v.name(), v.label(), v.type(), v.parentSavingsId(),
                v.parentAccountNumber(), v.clientId(), v.status(), v.transactionReference(), v.createdOn(), v.updatedOn());
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
